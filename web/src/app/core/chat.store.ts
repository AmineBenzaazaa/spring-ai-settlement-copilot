import { Injectable, computed, inject, signal } from '@angular/core';
import { Subscription } from 'rxjs';
import { CopilotApiService, CopilotHttpError } from './copilot-api.service';
import { I18nService } from './i18n.service';
import { ChatMessage, CopilotEvent, ToolCallTrace } from './models';

/**
 * The conversation as signals. RxJS carries the stream (cancellable, event by event); signals hold
 * the state the templates read. Each SSE event is folded into the last assistant message.
 */
@Injectable({ providedIn: 'root' })
export class ChatStore {
  private readonly api = inject(CopilotApiService);
  private readonly i18n = inject(I18nService);

  readonly messages = signal<ChatMessage[]>([]);
  readonly busy = signal(false);
  readonly conversationId = signal<string | undefined>(undefined);
  readonly lastAssistant = computed(() => [...this.messages()].reverse().find((m) => m.role === 'assistant'));
  readonly activeTraces = computed<ToolCallTrace[]>(() => this.lastAssistant()?.traces ?? []);

  private nextId = 1;
  private subscription?: Subscription;

  ask(question: string): void {
    const text = question.trim();
    if (!text || this.busy()) {
      return;
    }
    const assistantId = this.nextId + 1;
    this.messages.update((list) => [
      ...list,
      { id: this.nextId++, role: 'user', text, streaming: false, traces: [], sources: [] },
      { id: this.nextId++, role: 'assistant', text: '', streaming: true, traces: [], sources: [] },
    ]);
    this.busy.set(true);
    this.subscription = this.api
      .stream({ message: text, conversationId: this.conversationId(), language: this.i18n.lang() })
      .subscribe({
        next: (event) => this.apply(assistantId, event),
        error: (error: unknown) => {
          const message =
            error instanceof CopilotHttpError && error.status === 401 ? 'Session expired, please sign in again.' : this.i18n.t('errorGeneric');
          this.patch(assistantId, (m) => ({ ...m, streaming: false, error: message }));
          this.busy.set(false);
        },
        complete: () => {
          this.patch(assistantId, (m) => ({ ...m, streaming: false }));
          this.busy.set(false);
        },
      });
  }

  stop(): void {
    this.subscription?.unsubscribe();
    const last = this.lastAssistant();
    if (last?.streaming) {
      this.patch(last.id, (m) => ({ ...m, streaming: false }));
    }
    this.busy.set(false);
  }

  reset(): void {
    this.stop();
    this.messages.set([]);
    this.conversationId.set(undefined);
  }

  apply(id: number, event: CopilotEvent): void {
    switch (event.type) {
      case 'meta':
        this.conversationId.set(event.conversationId);
        break;
      case 'tool':
        this.patch(id, (m) => ({ ...m, traces: [...m.traces, event.trace] }));
        break;
      case 'token':
        this.patch(id, (m) => ({ ...m, text: m.text + event.text }));
        break;
      case 'chart':
        this.patch(id, (m) => ({ ...m, chart: event.chart }));
        break;
      case 'sources':
        this.patch(id, (m) => ({ ...m, sources: event.sources }));
        break;
      case 'verification':
        this.patch(id, (m) => ({ ...m, verification: event.verification }));
        break;
      case 'done':
        this.patch(id, (m) => ({ ...m, text: event.answer || m.text, durationMs: event.durationMs, streaming: false }));
        break;
      case 'error':
        this.patch(id, (m) => ({ ...m, error: event.message, streaming: false }));
        break;
    }
  }

  private patch(id: number, change: (m: ChatMessage) => ChatMessage): void {
    this.messages.update((list) => list.map((m) => (m.id === id ? change(m) : m)));
  }
}
