import { Component, input } from '@angular/core';

/** Brand mark: the route line drawing the M, ending in the destination dot (brand/logo/mse-mark.svg). Decorative. */
@Component({
  selector: 'app-logo',
  template: `
    <svg [attr.width]="size()" [attr.height]="size()" viewBox="0 0 32 32" aria-hidden="true" class="shrink-0">
      <rect width="32" height="32" rx="7" [attr.fill]="onBrand() ? 'rgb(255 255 255 / 0.12)' : 'rgb(var(--brand-tile))'" />
      <path d="M8 23.5V9.5l8 8.5 8-8.5v7.5" fill="none" [attr.stroke]="onBrand() ? '#fff' : 'rgb(var(--brand-ink))'" stroke-width="3.3" stroke-linecap="round" stroke-linejoin="round" />
      <circle cx="24" cy="23.4" r="2.7" [attr.fill]="onBrand() ? 'rgb(0 179 126)' : 'rgb(var(--brand-dot))'" />
    </svg>
  `,
  host: { class: 'inline-flex' },
})
export class Logo {
  readonly size = input(28);
  readonly onBrand = input(false);
}
