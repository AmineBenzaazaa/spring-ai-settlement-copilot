import { Component, computed, inject, input } from '@angular/core';
import { renderMarkdown } from '../../core/markdown';
import { I18nService } from '../../core/i18n.service';
import { ChatMessage } from '../../core/models';
import { ChartCard } from './chart-card';
import { Logo } from '../../shared/logo';

/** Lucide-style 24px paths for the source chips. */
const KIND_ICON: Record<string, string> = {
  statement: 'M14 3H6a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V9zM14 3v6h6M8 13h8M8 17h5',
  dispute: 'M12 3v18M5 7h14M5 7l-3 7a3 3 0 0 0 6 0zM19 7l-3 7a3 3 0 0 0 6 0z',
  courier: 'M3 6h11v10H3zM14 9h4l3 3v4h-7M7.5 19a1.5 1.5 0 1 0 0-3 1.5 1.5 0 0 0 0 3M17.5 19a1.5 1.5 0 1 0 0-3 1.5 1.5 0 0 0 0 3',
  order: 'M21 8 12 3 3 8v8l9 5 9-5zM3 8l9 5 9-5M12 13v8',
  overview: 'M4 20V10M10 20V4M16 20v-7M22 20H2',
  doc: 'M4 4h6a3 3 0 0 1 3 3v13a2 2 0 0 0-2-2H4zM20 4h-6a3 3 0 0 0-3 3v13a2 2 0 0 1 2-2h7z',
};

@Component({
  selector: 'app-message-bubble',
  imports: [ChartCard, Logo],
  template: `
    @if (message().role === 'user') {
      <div class="flex justify-end">
        <p class="max-w-[85%] rounded-2xl rounded-ee-md bg-accent px-4 py-2.5 text-sm text-accent-fg shadow-sm" data-testid="user-message">{{ message().text }}</p>
      </div>
    } @else {
      <article class="flex gap-3" data-testid="assistant-message">
        <app-logo [size]="30" class="mt-1" />
        <div class="min-w-0 flex-1 rounded-2xl rounded-ss-md border border-line bg-panel px-4 py-3 shadow-sm">
          @if (!message().text && message().streaming) {
            <p class="flex items-center gap-2 text-sm text-muted">
              <span class="flex gap-1"><span class="dot"></span><span class="dot [animation-delay:150ms]"></span><span class="dot [animation-delay:300ms]"></span></span>
              {{ i18n.t('thinking') }}
              @if (message().traces.length) { <span class="text-xs text-faint">· {{ i18n.tool(message().traces[message().traces.length - 1].tool) }}</span> }
            </p>
          }
          <div class="answer text-sm leading-relaxed text-fg" [innerHTML]="html()" data-testid="answer"></div>
          @if (message().streaming && message().text) { <span class="caret"></span> }

          @if (message().error) {
            <p class="mt-2 rounded-lg bg-danger/10 px-3 py-2 text-sm text-danger" role="alert">{{ message().error }}</p>
          }

          @if (message().chart; as chart) { <app-chart-card [spec]="chart" /> }

          @if (!message().streaming && (message().sources.length || message().verification)) {
            <footer class="mt-3 flex flex-wrap items-center gap-1.5 border-t border-line pt-3">
              @if (message().verification; as v) {
                @if (v.grounded) {
                  <span class="inline-flex items-center gap-1 rounded-full bg-success/10 px-2 py-0.5 text-[11px] font-medium text-success ring-1 ring-success/25" data-testid="grounded">
                    ✓ {{ v.checkedAmounts ? v.checkedAmounts + ' ' + i18n.t('verified') : i18n.t('noAmounts') }}
                  </span>
                } @else {
                  <span class="inline-flex items-center gap-1 rounded-full bg-danger/10 px-2 py-0.5 text-[11px] font-medium text-danger ring-1 ring-danger/25" data-testid="ungrounded">
                    ⚠ {{ i18n.t('ungrounded') }} {{ v.ungrounded.join(', ') }}
                  </span>
                }
              }
              @for (s of message().sources; track s.kind + s.id) {
                @if (s.url) {
                  <a [href]="s.url" target="_blank" rel="noopener" data-testid="source-chip"
                     class="focus-ring inline-flex max-w-full items-center gap-1 truncate rounded-full bg-subtle px-2 py-0.5 text-[11px] font-medium text-fg ring-1 ring-line transition hover:bg-accent/10 hover:text-accent hover:ring-accent/30">
                    <svg viewBox="0 0 24 24" class="size-3 shrink-0" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path [attr.d]="icon(s.kind)"/></svg>{{ s.label }}<span class="text-faint" aria-hidden="true">↗</span>
                  </a>
                } @else {
                  <span class="inline-flex max-w-full items-center gap-1 truncate rounded-full bg-subtle px-2 py-0.5 text-[11px] font-medium text-muted ring-1 ring-line" data-testid="source-chip">
                    <svg viewBox="0 0 24 24" class="size-3 shrink-0" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path [attr.d]="icon(s.kind)"/></svg>{{ s.label }}
                  </span>
                }
              }
              @if (message().durationMs) { <span class="ms-auto text-[11px] text-faint">{{ message().durationMs }} ms</span> }
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
    return KIND_ICON[kind] ?? KIND_ICON['doc'];
  }
}
