import { provideHttpClient } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, Subject } from 'rxjs';
import { ChatPage } from './chat-page';
import { AuthService } from '../../core/auth.service';
import { CopilotApiService } from '../../core/copilot-api.service';
import { CopilotEvent } from '../../core/models';
import { I18nService } from '../../core/i18n.service';

describe('ChatPage', () => {
  let events: Subject<CopilotEvent>;
  let api: { info: ReturnType<typeof vi.fn>; stream: ReturnType<typeof vi.fn> };
  const auth = { user: () => ({ username: 'atlas', role: 'MERCHANT', merchantId: 1, expiresAt: Date.now() + 1e6 }), logout: vi.fn() };

  beforeEach(() => {
    events = new Subject<CopilotEvent>();
    api = {
      info: vi.fn(() => of({ mode: 'demo', model: 'scripted-offline', description: '', languages: ['en', 'fr'], tools: ['getCourierBalances'], deskUrl: '' })),
      stream: vi.fn(() => events.asObservable()),
    };
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), { provide: CopilotApiService, useValue: api }, { provide: AuthService, useValue: auth }],
    });
    TestBed.inject(I18nService).setLang('en');
  });

  it('shows suggestions, then streams an answer for a clicked suggestion', async () => {
    const fixture = TestBed.createComponent(ChatPage);
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('[data-testid=mode-badge]')!.textContent).toContain('Offline demo model');
    expect(el.querySelector('[data-testid=user-chip]')!.textContent).toContain('atlas');
    const suggestions = el.querySelectorAll<HTMLButtonElement>('[data-testid=suggestion]');
    expect(suggestions.length).toBe(6);

    suggestions[0].click();
    events.next({ type: 'tool', trace: { index: 1, tool: 'getCourierBalances', arguments: '{}', status: 'OK', rows: 2, durationMs: 5, flagged: 0 } });
    events.next({ type: 'token', text: '**Rapido Express** owes you the most' });
    await fixture.whenStable();
    expect(el.querySelector('[data-testid=user-message]')!.textContent).toContain('Which courier owes me the most?');
    expect(el.querySelector('[data-testid=answer]')!.textContent).toContain('Rapido Express owes you the most');
    expect(el.querySelector('[data-testid=trace-item]')!.textContent).toContain('getCourierBalances');
    expect(el.textContent).toContain('Stop');
  });

  it('sends typed questions with Enter, keeps Shift+Enter for new lines, and switches language', async () => {
    const fixture = TestBed.createComponent(ChatPage);
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    const page = fixture.componentInstance as unknown as { draft: { set(v: string): void }; onEnter(e: Event): void };

    page.draft.set('Give me an overview');
    page.onEnter(new KeyboardEvent('keydown', { key: 'Enter', shiftKey: true }));
    expect(api.stream).not.toHaveBeenCalled();
    page.onEnter(new KeyboardEvent('keydown', { key: 'Enter' }));
    expect(api.stream).toHaveBeenCalledWith(expect.objectContaining({ message: 'Give me an overview', language: 'en' }));

    (el.querySelector('[data-testid=lang-fr]') as HTMLButtonElement).click();
    await fixture.whenStable();
    expect(el.querySelector('textarea')!.getAttribute('placeholder')).toContain('Posez une question');
    page.draft.set('   ');
    page.onEnter(new KeyboardEvent('keydown', { key: 'Enter' }));
    expect(api.stream).toHaveBeenCalledTimes(1);
  });
});
