import { Component, computed, inject, input } from '@angular/core';
import { I18nService } from '../../core/i18n.service';
import { ChatMessage } from '../../core/models';

/** Live view of the agent loop for the latest answer: every tool call, its rows and timing. */
@Component({
  selector: 'app-trace-panel',
  template: `
    <section class="rounded-2xl border border-slate-200 bg-white/80 p-4 shadow-sm backdrop-blur" data-testid="trace-panel">
      <header class="mb-3 flex items-center justify-between">
        <h2 class="text-xs font-bold uppercase tracking-wider text-slate-500">{{ i18n.t('traceTitle') }}</h2>
        @if (message()?.streaming) {
          <span class="inline-flex items-center gap-1 text-[11px] font-medium text-indigo-600"><span class="size-1.5 animate-pulse rounded-full bg-indigo-500"></span>live</span>
        }
      </header>

      @if (!traces().length) {
        <p class="text-xs text-slate-400">{{ i18n.t('traceEmpty') }}</p>
      }
      <ol class="relative space-y-3 border-l border-dashed border-slate-200 pl-4">
        @for (t of traces(); track t.index) {
          <li class="relative" data-testid="trace-item">
            <span class="absolute -left-[22px] top-1 grid size-3 place-items-center rounded-full ring-4 ring-white" [class]="dot(t.status)"></span>
            <p class="font-mono text-[12px] font-semibold text-slate-800">{{ t.tool }}<span class="font-normal text-slate-400">({{ args(t.arguments) }})</span></p>
            <p class="mt-0.5 flex flex-wrap items-center gap-x-2 text-[11px] text-slate-500">
              <span class="rounded px-1.5 py-px font-semibold" [class]="badge(t.status)">{{ t.status }}</span>
              <span>→ {{ t.rows }} {{ i18n.t('rows') }}</span>
              <span>{{ t.durationMs }} ms</span>
              @if (t.flagged) { <span class="font-medium text-amber-700">⚠ {{ t.flagged }} {{ i18n.t('flagged') }}</span> }
            </p>
          </li>
        }
      </ol>

      @if (message()?.verification; as v) {
        <div class="mt-4 rounded-xl px-3 py-2 text-[12px]" [class]="v.grounded ? 'bg-emerald-50 text-emerald-800' : 'bg-rose-50 text-rose-800'">
          <p class="font-semibold">{{ v.grounded ? '✓ ' + i18n.t('grounded') : '⚠ ' + i18n.t('notGrounded') }}</p>
          <p>{{ v.checkedAmounts ? v.checkedAmounts + ' ' + i18n.t('verified') : i18n.t('noAmounts') }}</p>
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
    return status === 'OK' ? 'bg-emerald-500' : status === 'NOT_FOUND' ? 'bg-amber-500' : 'bg-rose-500';
  }

  protected badge(status: string): string {
    return status === 'OK' ? 'bg-emerald-50 text-emerald-700' : status === 'NOT_FOUND' ? 'bg-amber-50 text-amber-700' : 'bg-rose-50 text-rose-700';
  }
}
