import { TestBed } from '@angular/core/testing';
import { Subject, throwError } from 'rxjs';
import { ChatStore } from './chat.store';
import { CopilotApiService, CopilotHttpError } from './copilot-api.service';
import { CopilotEvent } from './models';

describe('ChatStore', () => {
  let events: Subject<CopilotEvent>;
  let api: { stream: ReturnType<typeof vi.fn> };
  let store: ChatStore;

  beforeEach(() => {
    events = new Subject<CopilotEvent>();
    api = { stream: vi.fn(() => events.asObservable()) };
    TestBed.configureTestingModule({ providers: [{ provide: CopilotApiService, useValue: api }] });
    store = TestBed.inject(ChatStore);
  });

  it('folds streamed events into the assistant message', () => {
    store.ask('  Which courier owes me the most?  ');
    expect(store.busy()).toBe(true);
    expect(api.stream).toHaveBeenCalledWith({ message: 'Which courier owes me the most?', conversationId: undefined, language: expect.any(String) });

    events.next({ type: 'meta', conversationId: 'c1', mode: 'demo', model: 'm', language: 'en' });
    events.next({ type: 'tool', trace: { index: 1, tool: 'getCourierBalances', arguments: '{}', status: 'OK', rows: 2, durationMs: 9, flagged: 0 } });
    events.next({ type: 'token', text: '**Rapido** ' });
    events.next({ type: 'token', text: 'owes' });
    expect(store.lastAssistant()!.text).toBe('**Rapido** owes');
    expect(store.activeTraces()).toHaveLength(1);

    events.next({ type: 'chart', chart: { type: 'bar', title: 't', labels: ['a'], datasets: [{ label: 'x', data: [1] }], unit: 'MAD' } });
    events.next({ type: 'sources', sources: [{ kind: 'courier', id: '2', label: 'Rapido', url: 'http://desk' }] });
    events.next({ type: 'verification', verification: { grounded: true, checkedAmounts: 2, ungrounded: [] } });
    events.next({ type: 'done', conversationId: 'c1', answer: '**Rapido** owes 1.00 MAD', durationMs: 42 });
    events.complete();

    const answer = store.lastAssistant()!;
    expect(answer).toMatchObject({ text: '**Rapido** owes 1.00 MAD', streaming: false, durationMs: 42 });
    expect(answer.chart?.type).toBe('bar');
    expect(answer.sources).toHaveLength(1);
    expect(answer.verification?.grounded).toBe(true);
    expect(store.busy()).toBe(false);
    expect(store.conversationId()).toBe('c1');
    expect(store.messages().map((m) => m.role)).toEqual(['user', 'assistant']);

    store.ask('And Rapido?');
    expect(api.stream).toHaveBeenLastCalledWith(expect.objectContaining({ conversationId: 'c1' }));
  });

  it('ignores blank questions and questions while busy', () => {
    store.ask('   ');
    expect(api.stream).not.toHaveBeenCalled();
    store.ask('one');
    store.ask('two');
    expect(api.stream).toHaveBeenCalledTimes(1);
  });

  it('shows server error events and transport errors', () => {
    store.ask('q');
    events.next({ type: 'error', code: 'MODEL_ERROR', message: 'boom' });
    expect(store.lastAssistant()!.error).toBe('boom');
    events.complete();

    api.stream.mockReturnValueOnce(throwError(() => new CopilotHttpError(401)));
    store.ask('again');
    expect(store.lastAssistant()!.error).toContain('sign in again');
    api.stream.mockReturnValueOnce(throwError(() => new Error('net')));
    store.ask('again 2');
    expect(store.lastAssistant()!.error).toBeTruthy();
    expect(store.busy()).toBe(false);
  });

  it('stops a running answer and resets the conversation', () => {
    store.ask('q');
    events.next({ type: 'meta', conversationId: 'c9', mode: 'demo', model: 'm', language: 'en' });
    store.stop();
    expect(store.busy()).toBe(false);
    expect(store.lastAssistant()!.streaming).toBe(false);
    expect(events.observed).toBe(false);
    store.reset();
    expect(store.messages()).toEqual([]);
    expect(store.conversationId()).toBeUndefined();
  });
});
