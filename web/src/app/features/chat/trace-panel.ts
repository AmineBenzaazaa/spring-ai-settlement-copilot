import { Component, computed, inject, input } from '@angular/core';
import { I18nService } from '../../core/i18n.service';
import { ChatMessage } from '../../core/models';

/** Live view of the agent loop for the latest answer: every tool call, its rows and timing. */
@Component({
  selector: 'app-trace-panel',
  template: `
    <section class="rounded-2xl border border-line bg-panel p-4 shadow-sm" data-testid="trace-panel">
      <header class="mb-3 flex items-center justify-between">
        <h2 class="text-[10.5px] font-semibold uppercase tracking-[0.08em] text-faint">{{ i18n.t('traceTitle') }}</h2>
        @if (message()?.streaming) {
          <span class="inline-flex items-center gap-1 text-[11px] font-medium text-accent"><span class="size-1.5 animate-pulse rounded-full bg-accent"></span>live</span>
        }
      </header>

      @if (!traces().length) {
        <p class="text-xs text-muted">{{ i18n.t('traceEmpty') }}</p>
      }
      <ol class="relative space-y-3 border-s border-dashed border-line ps-4">
        @for (t of traces(); track t.index) {
          <li class="relative" data-testid="trace-item">
            <span class="absolute -start-[22px] top-1 grid size-3 place-items-center rounded-full ring-4 ring-panel" [class]="dot(t.status)"></span>
            <p class="text-[12.5px] font-medium text-fg">{{ i18n.tool(t.tool) }}</p>
            <p class="truncate font-mono text-[11px] text-faint" [attr.title]="t.tool + '(' + args(t.arguments) + ')'">{{ t.tool }}<span>({{ args(t.arguments) }})</span></p>
            <p class="mt-1 flex flex-wrap items-center gap-x-2 text-[11px] text-muted">
              <span class="rounded px-1.5 py-px font-semibold" [class]="badge(t.status)" [attr.title]="t.status">{{ i18n.label(t.status) }}</span>
              <span>{{ t.rows }} {{ i18n.t('rows') }}</span>
              <span>{{ t.durationMs }} ms</span>
              @if (t.flagged) { <span class="font-medium text-warn">⚠ {{ t.flagged }} {{ i18n.t('flagged') }}</span> }
            </p>
          </li>
        }
      </ol>

      @if (message()?.verification; as v) {
        <div class="mt-4 rounded-xl px-3 py-2 text-[12px]" [class]="v.grounded ? 'bg-success/10 text-success' : 'bg-danger/10 text-danger'">
          <p class="font-semibold">{{ v.grounded ? '✓ ' + i18n.t('grounded') : '⚠ ' + i18n.t('notGrounded') }}</p>
          <p class="text-fg/80">{{ v.checkedAmounts ? v.checkedAmounts + ' ' + i18n.t('verified') : i18n.t('noAmounts') }}</p>
        </div>
      }
    </section>
  `,
})
export class TracePanel {
  readonly message = input<ChatMessage | undefined>();
  protected readonly i18n = inject(I18nService);
  protected readonly traces = computed(() => this.message()?.traces ?? []);

  protected args(json: string): string {
    try {
      const parsed = JSON.parse(json) as Record<string, unknown>;
      return Object.entries(parsed)
        .filter(([, v]) => v !== null && v !== '')
        .map(([k, v]) => `${k}: ${JSON.stringify(v)}`)
        .join(', ');
    } catch {
      return json;
    }
  }

  protected dot(status: string): string {
    return status === 'OK' ? 'bg-success' : status === 'NOT_FOUND' ? 'bg-warn' : 'bg-danger';
  }

  protected badge(status: string): string {
    return status === 'OK' ? 'bg-success/10 text-success' : status === 'NOT_FOUND' ? 'bg-warn/10 text-warn' : 'bg-danger/10 text-danger';
  }
}
