import { Component, computed, inject, input } from '@angular/core';
import { I18nService } from '../../core/i18n.service';
import { ChatMessage } from '../../core/models';
import { describeCall, describeResult } from '../../core/describe';

/** Live view of the agent loop for the latest answer: every tool call, its rows and timing. */
@Component({
  selector: 'app-trace-panel',
  host: { class: 'block' },
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
            <p class="text-[13px] leading-snug text-fg">{{ sentence(t.tool, t.arguments) }}</p>
            <p class="mt-0.5 text-[12px]" [class]="t.status === 'OK' ? 'text-success' : 'text-warn'">{{ result(t.status, t.rows) }}</p>
            @if (t.flagged) { <p class="mt-0.5 text-[11.5px] font-medium text-warn">⚠ {{ t.flagged }} {{ i18n.t('flagged') }}</p> }
            <details class="group mt-1">
              <summary class="cursor-pointer list-none text-[11px] text-faint hover:text-muted [&::-webkit-details-marker]:hidden">
                <span class="inline-block transition group-open:rotate-90" aria-hidden="true">›</span> {{ i18n.t('techDetails') }}
              </summary>
              <div class="mt-1 space-y-0.5 rounded-md bg-subtle px-2 py-1.5 font-mono text-[11px] text-muted">
                <p class="break-all">{{ t.tool }}({{ args(t.arguments) }})</p>
                <p>{{ t.status }} · {{ i18n.rows(t.rows) }} · {{ t.durationMs }} ms</p>
              </div>
            </details>
          </li>
        }
      </ol>

      @if (message() && !message()!.streaming && !message()!.sources.length && message()!.traces.length) {
        <div class="mt-4 rounded-xl bg-subtle px-3 py-2 text-[12px] text-muted" data-testid="no-records">{{ i18n.t('noRecordsHint') }}</div>
      } @else if (message()?.verification; as v) {
        <div class="mt-4 rounded-xl px-3 py-2 text-[12px]" [class]="v.grounded ? 'bg-success/10 text-success' : 'bg-danger/10 text-danger'" data-testid="verification">
          <p class="font-semibold">{{ v.grounded ? '✓ ' + i18n.t('grounded') : '⚠ ' + i18n.t('notGrounded') }}</p>
          <p class="text-fg/80">{{ v.checkedAmounts ? i18n.verified(v.checkedAmounts) : i18n.t('noAmounts') }}</p>
        </div>
      }
      @if (message()?.durationMs; as ms) {
        <p class="mt-3 text-[11px] text-faint">{{ i18n.t('responseTime') }} {{ ms }} ms</p>
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

  protected sentence(tool: string, json: string): string {
    return describeCall(this.i18n.lang(), tool, json);
  }

  protected result(status: string, rows: number): string {
    return describeResult(this.i18n.lang(), status, rows);
  }

  protected dot(status: string): string {
    return status === 'OK' ? 'bg-success' : status === 'NOT_FOUND' ? 'bg-warn' : 'bg-danger';
  }

  protected badge(status: string): string {
    return status === 'OK' ? 'bg-success/10 text-success' : status === 'NOT_FOUND' ? 'bg-warn/10 text-warn' : 'bg-danger/10 text-danger';
  }
}
