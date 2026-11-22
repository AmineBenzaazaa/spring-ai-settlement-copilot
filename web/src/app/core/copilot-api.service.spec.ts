import { provideHttpClient } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { firstValueFrom, toArray } from 'rxjs';
import { AuthService } from './auth.service';
import { CopilotApiService, CopilotHttpError } from './copilot-api.service';
import { CopilotEvent } from './models';

function sseResponse(chunks: string[], status = 200): Response {
  const encoder = new TextEncoder();
  const body = new ReadableStream<Uint8Array>({
    start(controller) {
      chunks.forEach((c) => controller.enqueue(encoder.encode(c)));
      controller.close();
    },
  });
  return new Response(body, { status, headers: { 'Content-Type': 'text/event-stream' } });
}

describe('CopilotApiService.stream', () => {
  let api: CopilotApiService;
  let auth: { accessToken: () => string | null; refresh: ReturnType<typeof vi.fn> };
  let fetchMock: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    auth = { accessToken: () => 'tok', refresh: vi.fn().mockResolvedValue(true) };
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), { provide: AuthService, useValue: auth }],
    });
    api = TestBed.inject(CopilotApiService);
    fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
  });

  afterEach(() => vi.unstubAllGlobals());

  it('posts the question with the bearer token and emits parsed events in order', async () => {
    fetchMock.mockResolvedValue(
      sseResponse(['event:meta\ndata:{"conversationId":"c1"}\n\nevent:tok', 'en\ndata:{"text":"Hi"}\n\n', 'event:done\ndata:{"answer":"Hi"}']),
    );
    const events = await firstValueFrom(api.stream({ message: 'q', language: 'fr' }).pipe(toArray()));
    expect(events.map((e: CopilotEvent) => e.type)).toEqual(['meta', 'token', 'done']);
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe('/api/copilot/chat/stream');
    expect(init.headers.Authorization).toBe('Bearer tok');
    expect(JSON.parse(init.body)).toEqual({ message: 'q', language: 'fr' });
  });

  it('refreshes once on 401 and retries', async () => {
    fetchMock.mockResolvedValueOnce(new Response(null, { status: 401 })).mockResolvedValueOnce(sseResponse(['event:token\ndata:{"text":"ok"}\n\n']));
    const events = await firstValueFrom(api.stream({ message: 'q' }).pipe(toArray()));
    expect(auth.refresh).toHaveBeenCalledTimes(1);
    expect(events).toEqual([{ type: 'token', text: 'ok' }]);
  });

  it('errors with the HTTP status when the request fails', async () => {
    fetchMock.mockResolvedValue(new Response(null, { status: 400 }));
    await expect(firstValueFrom(api.stream({ message: 'q' }))).rejects.toEqual(new CopilotHttpError(400));
  });

  it('aborts the request when unsubscribed', () => {
    let signal: AbortSignal | undefined;
    fetchMock.mockImplementation((_: string, init: RequestInit) => {
      signal = init.signal ?? undefined;
      return new Promise(() => undefined);
    });
    api.stream({ message: 'q' }).subscribe().unsubscribe();
    expect(signal?.aborted).toBe(true);
  });

  it('swallows AbortError but reports other failures', async () => {
    fetchMock.mockRejectedValue(Object.assign(new Error('x'), { name: 'AbortError' }));
    const next = vi.fn();
    const error = vi.fn();
    api.stream({ message: 'q' }).subscribe({ next, error });
    await new Promise((r) => setTimeout(r));
    expect(error).not.toHaveBeenCalled();

    fetchMock.mockRejectedValue(new TypeError('network'));
    await expect(firstValueFrom(api.stream({ message: 'q' }))).rejects.toBeInstanceOf(TypeError);
  });
});
