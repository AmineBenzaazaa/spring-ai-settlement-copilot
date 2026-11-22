import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { AuthService } from './auth.service';
import { CopilotEvent, CopilotInfo, Lang } from './models';
import { SseParser } from './sse';

export interface AskRequest {
  message: string;
  conversationId?: string;
  language?: Lang;
}

export class CopilotHttpError extends Error {
  constructor(readonly status: number) {
    super(`Copilot request failed with HTTP ${status}`);
  }
}

@Injectable({ providedIn: 'root' })
export class CopilotApiService {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);

  info(): Observable<CopilotInfo> {
    return this.http.get<CopilotInfo>('/api/copilot/info');
  }

  /**
   * POST /api/copilot/chat/stream, read as Server-Sent Events. A cold Observable: nothing is sent
   * until subscribe, and unsubscribing aborts the HTTP request (the server then stops the loop).
   * One retry after refreshing an expired access token.
   */
  stream(request: AskRequest): Observable<CopilotEvent> {
    return new Observable<CopilotEvent>((subscriber) => {
      const controller = new AbortController();
      const send = () =>
        fetch('/api/copilot/chat/stream', {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            Accept: 'text/event-stream',
            Authorization: `Bearer ${this.auth.accessToken() ?? ''}`,
          },
          body: JSON.stringify(request),
          signal: controller.signal,
        });

      (async () => {
        let response = await send();
        if (response.status === 401 && (await this.auth.refresh())) {
          response = await send();
        }
        if (!response.ok || !response.body) {
          throw new CopilotHttpError(response.status);
        }
        const reader = response.body.getReader();
        const decoder = new TextDecoder();
        const parser = new SseParser();
        for (;;) {
          const { done, value } = await reader.read();
          if (done) {
            break;
          }
          parser.feed(decoder.decode(value, { stream: true })).forEach((e) => subscriber.next(e));
        }
        parser.flush().forEach((e) => subscriber.next(e));
        subscriber.complete();
      })().catch((error: unknown) => {
        if ((error as { name?: string })?.name !== 'AbortError') {
          subscriber.error(error);
        }
      });

      return () => controller.abort();
    });
  }
}
