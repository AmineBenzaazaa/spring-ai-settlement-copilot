import { Component, computed, inject, input } from '@angular/core';
import { renderMarkdown } from '../../core/markdown';
import { I18nService } from '../../core/i18n.service';
import { ChatMessage } from '../../core/models';
import { ChartCard } from './chart-card';

const KIND_ICON: Record<string, string> = {
  statement: '📄',
  dispute: '⚖️',
  courier: '🚚',
  order: '📦',
  overview: '📊',
  doc: '📘',
};

@Component({
  selector: 'app-message-bubble',
  imports: [ChartCard],
  template: `
    @if (message().role === 'user') {
      <div class="flex justify-end">
        <p class="max-w-[85%] rounded-2xl rounded-br-md bg-gradient-to-br from-indigo-600 to-violet-600 px-4 py-2.5 text-sm text-white shadow-md shadow-indigo-500/20"
           data-testid="user-message">{{ message().text }}</p>
      </div>
    } @else {
      <article class="flex gap-3" data-testid="assistant-message">
        <div class="mt-1 grid size-8 shrink-0 place-items-center rounded-lg bg-gradient-to-br from-indigo-500 to-fuchsia-500 text-sm font-black text-white">S</div>
        <div class="min-w-0 flex-1 rounded-2xl rounded-tl-md border border-slate-200 bg-white px-4 py-3 shadow-sm">
          @if (!message().text && message().streaming) {
            <p class="flex items-center gap-2 text-sm text-slate-500">
              <span class="flex gap-1"><span class="dot"></span><span class="dot [animation-delay:150ms]"></span><span class="dot [animation-delay:300ms]"></span></span>
              {{ i18n.t('thinking') }}
              @if (message().traces.length) { <span class="text-xs text-slate-400">· {{ message().traces[message().traces.length - 1].tool }}()</span> }
            </p>
          }
          <div class="answer text-sm leading-relaxed text-slate-800" [innerHTML]="html()" data-testid="answer"></div>
          @if (message().streaming && message().text) { <span class="caret"></span> }

          @if (message().error) {
            <p class="mt-2 rounded-lg bg-rose-50 px-3 py-2 text-sm text-rose-700" role="alert">{{ message().error }}</p>
          }

          @if (message().chart; as chart) { <app-chart-card [spec]="chart" /> }

          @if (!message().streaming && (message().sources.length || message().verification)) {
            <footer class="mt-3 flex flex-wrap items-center gap-1.5 border-t border-slate-100 pt-3">
              @if (message().verification; as v) {
                @if (v.grounded) {
                  <span class="inline-flex items-center gap-1 rounded-full bg-emerald-50 px-2 py-0.5 text-[11px] font-medium text-emerald-700 ring-1 ring-emerald-200" data-testid="grounded">
                    ✓ {{ v.checkedAmounts ? v.checkedAmounts + ' ' + i18n.t('verified') : i18n.t('noAmounts') }}
                  </span>
                } @else {
                  <span class="inline-flex items-center gap-1 rounded-full bg-rose-50 px-2 py-0.5 text-[11px] font-medium text-rose-700 ring-1 ring-rose-200" data-testid="ungrounded">
                    ⚠ {{ i18n.t('ungrounded') }} {{ v.ungrounded.join(', ') }}
                  </span>
                }
              }
              @for (s of message().sources; track s.kind + s.id) {
                @if (s.url) {
                  <a [href]="s.url" target="_blank" rel="noopener" data-testid="source-chip"
                     class="inline-flex max-w-full items-center gap-1 truncate rounded-full bg-slate-100 px-2 py-0.5 text-[11px] font-medium text-slate-700 ring-1 ring-slate-200 transition hover:bg-indigo-50 hover:text-indigo-700 hover:ring-indigo-200">
                    <span>{{ icon(s.kind) }}</span>{{ s.label }}<span class="text-slate-400">↗</span>
                  </a>
                } @else {
                  <span class="inline-flex max-w-full items-center gap-1 truncate rounded-full bg-slate-100 px-2 py-0.5 text-[11px] font-medium text-slate-600 ring-1 ring-slate-200" data-testid="source-chip">
                    <span>{{ icon(s.kind) }}</span>{{ s.label }}
                  </span>
                }
              }
              @if (message().durationMs) { <span class="ml-auto text-[11px] text-slate-400">{{ message().durationMs }} ms</span> }
            </footer>
          }
        </div>
      </article>
    }
  `,
})
export class MessageBubble {
  readonly message = input.required<ChatMessage>();
  protected readonly i18n = inject(I18nService);
  protected readonly html = computed(() => renderMarkdown(this.message().text));

  protected icon(kind: string): string {
    return KIND_ICON[kind] ?? '•';
  }
}
