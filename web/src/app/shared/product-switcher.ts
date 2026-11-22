import { Component, computed, inject, input } from '@angular/core';
import { BRAND, CURRENT_PRODUCT, PRODUCTS, ProductId } from '../brand/brand';
import { I18nService } from '../core/i18n.service';
import { Logo } from './logo';
import { MenuState } from './menu';

const ICONS: Record<ProductId, string> = {
  dispatch: 'M4 17a2 2 0 1 0 4 0 2 2 0 0 0-4 0M16 7a2 2 0 1 0 4 0 2 2 0 0 0-4 0M8 17h5a3 3 0 0 0 3-3V9',
  settle: 'M3 7h18v12H3zM3 11h18M7 15h3',
  copilot: 'M12 3l1.8 4.7L18.5 9.5l-4.7 1.8L12 16l-1.8-4.7L5.5 9.5l4.7-1.8zM18 15l.9 2.1L21 18l-2.1.9L18 21l-.9-2.1L15 18l2.1-.9z',
};

/** Brand lockup that opens the suite switcher (Dispatch, Settle, Copilot). */
@Component({
  selector: 'app-product-switcher',
  imports: [Logo],
  hostDirectives: [MenuState],
  host: { class: 'relative' },
  template: `
    <button type="button" aria-haspopup="menu" [attr.aria-expanded]="menu.open()" (click)="menu.toggle()"
            [attr.aria-label]="i18n.t('switchProduct')" data-testid="product-switcher"
            class="focus-ring flex items-center gap-2 rounded-lg py-1 pe-1.5 ps-1 transition-colors hover:bg-subtle" [class.bg-subtle]="menu.open()">
      <app-logo [size]="28" />
      <span class="whitespace-nowrap text-[15px] font-semibold tracking-tight text-fg">{{ brand.name }} <span class="font-medium text-muted">{{ brand.product }}</span></span>
      <svg viewBox="0 0 24 24" class="size-3.5 text-faint" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" aria-hidden="true"><path d="m7 15 5 5 5-5M7 9l5-5 5 5"/></svg>
    </button>
    @if (menu.open()) {
      <div role="menu" [attr.aria-label]="i18n.t('switchProduct')"
           class="absolute start-0 top-full z-50 mt-2 w-72 rounded-xl border border-line bg-panel p-1.5 shadow-xl shadow-black/10">
        <p class="px-2.5 pb-1.5 pt-1 text-[10.5px] font-semibold uppercase tracking-[0.08em] text-faint">{{ brand.suite }}</p>
        @for (p of products(); track p.id) {
          <a role="menuitem" [href]="p.id === current ? '/' : p.url" (click)="menu.close()" [attr.aria-current]="p.id === current ? 'page' : null"
             [attr.data-testid]="'product-' + p.id"
             class="flex items-center gap-2.5 rounded-lg px-2.5 py-2 text-[13px] text-fg outline-none hover:bg-subtle focus-visible:bg-subtle">
            <span class="grid size-8 place-items-center rounded-lg" [class]="p.id === current ? 'bg-accent text-accent-fg' : 'bg-subtle text-muted'">
              <svg viewBox="0 0 24 24" class="size-4" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path [attr.d]="icons[p.id]"/></svg>
            </span>
            <span class="min-w-0 flex-1">
              <span class="block font-medium">{{ brand.name }} {{ p.name }}</span>
              <span class="block truncate text-[12px] text-muted">{{ i18n.t(p.id === 'dispatch' ? 'suiteDispatch' : p.id === 'settle' ? 'suiteSettle' : 'suiteCopilot') }}</span>
            </span>
            @if (p.id === current) {
              <svg viewBox="0 0 24 24" class="size-4 text-accent" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" aria-hidden="true"><path d="m5 12 5 5L20 7"/></svg>
            }
          </a>
        }
      </div>
    }
  `,
})
export class ProductSwitcher {
  /** Settle URL reported by the copilot API (`deskUrl`), when known. */
  readonly settleUrl = input<string | undefined>();
  protected readonly menu = inject(MenuState);
  protected readonly i18n = inject(I18nService);
  protected readonly brand = BRAND;
  protected readonly current = CURRENT_PRODUCT;
  protected readonly icons = ICONS;
  protected readonly products = computed(() =>
    PRODUCTS.map((p) => (p.id === 'settle' && this.settleUrl() ? { ...p, url: this.settleUrl()! } : p)),
  );
}
