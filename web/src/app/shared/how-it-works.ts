import { Component, HostListener, computed, inject, model } from '@angular/core';
import { HOW_IT_WORKS } from '../brand/suite-content';
import { I18nService } from '../core/i18n.service';

const BADGE: Record<string, string> = {
  dispatch: 'bg-accent/10 text-accent',
  settle: 'bg-signal/15 text-success',
  copilot: 'bg-info/10 text-info',
  courier: 'bg-subtle text-muted',
};

/** "How it works": the order-to-cash flow across MSE Dispatch, Settle and Copilot, as a stepper. */
@Component({
  selector: 'app-how-it-works',
  template: `
    @if (open()) {
      <div class="fixed inset-0 z-[60] grid place-items-center bg-black/40 p-4 backdrop-blur-sm" (click)="open.set(false)">
        <section role="dialog" aria-modal="true" aria-labelledby="hiw-title" data-testid="how-it-works"
                 class="max-h-[90dvh] w-full max-w-3xl overflow-y-auto rounded-2xl border border-line bg-panel p-6 shadow-2xl" (click)="$event.stopPropagation()">
          <header class="mb-5 flex items-start justify-between gap-4">
            <div>
              <h2 id="hiw-title" class="text-lg font-semibold tracking-tight text-fg">{{ flow().title }}</h2>
              <p class="mt-1 text-sm text-muted">{{ flow().intro }}</p>
            </div>
            <button type="button" (click)="open.set(false)" [attr.aria-label]="i18n.t('close')" class="focus-ring grid size-8 shrink-0 place-items-center rounded-lg text-muted hover:bg-subtle hover:text-fg">
              <svg viewBox="0 0 24 24" class="size-4" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" aria-hidden="true"><path d="M6 6l12 12M18 6 6 18"/></svg>
            </button>
          </header>
          <ol class="grid gap-3 sm:grid-cols-3">
            @for (s of flow().steps; track $index; let i = $index) {
              <li class="relative rounded-xl border border-line bg-bg p-4">
                <div class="mb-2 flex items-center gap-2">
                  <span class="grid size-7 place-items-center rounded-full bg-accent text-[12px] font-semibold text-accent-fg">{{ i + 1 }}</span>
                  <span class="rounded-full px-2 py-0.5 text-[10.5px] font-semibold uppercase tracking-wide" [class]="badge(s.product)">{{ label(s.product) }}</span>
                </div>
                <p class="text-[13.5px] font-semibold text-fg">{{ s.title }}</p>
                <p class="mt-1 text-[12.5px] leading-snug text-muted">{{ s.detail }}</p>
              </li>
            }
          </ol>
        </section>
      </div>
    }
  `,
})
export class HowItWorks {
  readonly open = model(false);
  protected readonly i18n = inject(I18nService);
  protected readonly flow = computed(() => HOW_IT_WORKS[this.i18n.lang()]);

  protected badge(p: string): string {
    return BADGE[p] ?? BADGE['courier'];
  }

  protected label(p: string): string {
    return p === 'courier' ? (this.i18n.lang() === 'fr' ? 'Livreur' : 'Courier') : `MSE ${p.charAt(0).toUpperCase()}${p.slice(1)}`;
  }

  @HostListener('document:keydown.escape')
  onEscape(): void {
    this.open.set(false);
  }
}
