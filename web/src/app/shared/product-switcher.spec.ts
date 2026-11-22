import { TestBed } from '@angular/core/testing';
import { ProductSwitcher } from './product-switcher';
import { I18nService } from '../core/i18n.service';

describe('ProductSwitcher', () => {
  beforeEach(() => TestBed.inject(I18nService).setLang('en'));

  it('opens the suite menu, marks Copilot as current and links the other products', async () => {
    const fixture = TestBed.createComponent(ProductSwitcher);
    fixture.componentRef.setInput('settleUrl', 'http://localhost:3201');
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('MSE Copilot');
    expect(el.querySelector('[role=menu]')).toBeNull();

    (el.querySelector('[data-testid=product-switcher]') as HTMLButtonElement).click();
    await fixture.whenStable();
    expect(el.querySelector('[data-testid=product-switcher]')!.getAttribute('aria-expanded')).toBe('true');
    expect(el.querySelector('[data-testid=product-dispatch]')!.getAttribute('href')).toBe('http://localhost:3100');
    expect(el.querySelector('[data-testid=product-settle]')!.getAttribute('href')).toBe('http://localhost:3201');
    expect(el.querySelector('[data-testid=product-copilot]')!.getAttribute('aria-current')).toBe('page');

    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    await fixture.whenStable();
    expect(el.querySelector('[role=menu]')).toBeNull();
  });
});
