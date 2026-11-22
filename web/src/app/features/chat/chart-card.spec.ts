import { TestBed } from '@angular/core/testing';
import { ChartCard } from './chart-card';

describe('ChartCard', () => {
  it('renders the title and a canvas for the server-built spec', async () => {
    const quiet = vi.spyOn(console, 'error').mockImplementation(() => undefined); // jsdom has no 2D canvas
    const fixture = TestBed.createComponent(ChartCard);
    fixture.componentRef.setInput('spec', { type: 'bar', title: 'Balance owed by courier', labels: ['Rapido'], datasets: [{ label: 'x', data: [4315] }], unit: 'MAD' });
    await fixture.whenStable();
    expect(fixture.nativeElement.textContent).toContain('Balance owed by courier');
    expect(fixture.nativeElement.textContent).toContain('(MAD)');
    expect(fixture.nativeElement.querySelector('canvas')).not.toBeNull();
    fixture.componentRef.setInput('spec', { type: 'doughnut', title: 'Lines', labels: ['MATCHED'], datasets: [{ label: 'n', data: [11] }], unit: null });
    await fixture.whenStable();
    expect(fixture.nativeElement.textContent).not.toContain('(MAD)');
    fixture.destroy();
    quiet.mockRestore();
  });
});
