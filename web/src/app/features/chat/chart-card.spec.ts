import { TestBed } from '@angular/core/testing';
import { ChartCard } from './chart-card';
import { I18nService } from '../../core/i18n.service';

describe('ChartCard', () => {
  beforeEach(() => TestBed.inject(I18nService).setLang('en'));

  it('renders the title and a canvas when there are at least 3 data points', async () => {
    const quiet = vi.spyOn(console, 'error').mockImplementation(() => undefined); // jsdom has no 2D canvas
    const fixture = TestBed.createComponent(ChartCard);
    fixture.componentRef.setInput('spec', { type: 'bar', title: 'Balance owed by courier', labels: ['A', 'B', 'C'], datasets: [{ label: 'x', data: [4315, 530, 10] }], unit: 'MAD' });
    await fixture.whenStable();
    expect(fixture.nativeElement.textContent).toContain('Balance owed by courier');
    expect(fixture.nativeElement.textContent).toContain('(MAD)');
    expect(fixture.nativeElement.querySelector('canvas')).not.toBeNull();
    fixture.componentRef.setInput('spec', { type: 'doughnut', title: 'Lines', labels: ['MATCHED', 'AMOUNT_MISMATCH', 'UNKNOWN_TRACKING'], datasets: [{ label: 'n', data: [11, 1, 1] }], unit: null });
    await fixture.whenStable();
    expect(fixture.nativeElement.textContent).not.toContain('(MAD)');
    fixture.destroy();
    quiet.mockRestore();
  });

  it('shows a compact summary instead of a chart for fewer than 3 points', async () => {
    const fixture = TestBed.createComponent(ChartCard);
    fixture.componentRef.setInput('spec', { type: 'bar', title: 'Open disputes', labels: ['AMOUNT_MISMATCH'], datasets: [{ label: 'claimed', data: [480.5] }], unit: 'MAD' });
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('canvas')).toBeNull();
    expect(el.querySelector('[data-testid=chart-summary]')!.textContent).toContain('Amount mismatch');
    expect(el.textContent).toContain('480.50');
  });
});
