import { Component, computed, inject, input } from '@angular/core';
import { GLOSSARY, TermKey } from '../brand/suite-content';
import { I18nService } from '../core/i18n.service';

/** A domain word with an info tooltip from the shared MSE glossary (hover or keyboard focus). */
@Component({
  selector: 'app-term',
  host: { class: 'group/term relative inline-flex items-center gap-1' },
  template: `
    <ng-content />
    <button type="button" class="focus-ring inline-grid size-4 place-items-center rounded-full text-faint hover:text-accent focus-visible:text-accent"
            [attr.aria-label]="entry().term + ': ' + entry().definition" [attr.aria-describedby]="id">
      <svg viewBox="0 0 24 24" class="size-3.5" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" aria-hidden="true"><circle cx="12" cy="12" r="9"/><path d="M12 11v5M12 8h.01"/></svg>
    </button>
    <span role="tooltip" [id]="id"
          class="pointer-events-none invisible absolute start-0 top-full z-50 mt-1.5 w-64 rounded-lg border border-line bg-panel p-2.5 text-start text-[12px] font-normal normal-case leading-snug tracking-normal text-muted opacity-0 shadow-xl shadow-black/10 transition group-hover/term:visible group-hover/term:opacity-100 group-focus-within/term:visible group-focus-within/term:opacity-100">
      <span class="mb-0.5 block font-semibold text-fg">{{ entry().term }}</span>{{ entry().definition }}
    </span>
  `,
})
export class Term {
  readonly key = input.required<TermKey>();
  private readonly i18n = inject(I18nService);
  protected readonly id = `term-${Math.random().toString(36).slice(2, 8)}`;
  protected readonly entry = computed(() => GLOSSARY[this.key()][this.i18n.lang()]);
}
