import { TestBed } from '@angular/core/testing';
import { ModeBadge } from './mode-badge';
import { CopilotInfo } from '../core/models';

const info = (mode: 'demo' | 'anthropic', model: string): CopilotInfo =>
  ({ mode, model, description: 'Anthropic Claude via Spring AI', languages: ['en', 'fr'], tools: [], deskUrl: '' });

describe('ModeBadge', () => {
  it('makes the offline demo mode obvious', async () => {
    const fixture = TestBed.createComponent(ModeBadge);
    fixture.componentRef.setInput('info', info('demo', 'scripted-offline'));
    await fixture.whenStable();
    const badge = fixture.nativeElement.querySelector('[data-testid=mode-badge]') as HTMLElement;
    expect(badge.textContent).toContain('Demo mode');
    expect(badge.getAttribute('title')).toContain('sample data');
    expect(badge.className).toContain('amber');
  });

  it('shows the Claude model id in anthropic mode, nothing without info', async () => {
    const fixture = TestBed.createComponent(ModeBadge);
    await fixture.whenStable();
    expect(fixture.nativeElement.textContent.trim()).toBe('');
    fixture.componentRef.setInput('info', info('anthropic', 'claude-opus-5-5'));
    await fixture.whenStable();
    expect(fixture.nativeElement.textContent).toContain('claude-opus-5-5');
  });
});
