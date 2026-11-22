import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { LoginPage } from './login';
import { AuthService } from '../../core/auth.service';
import { CopilotApiService } from '../../core/copilot-api.service';
import { I18nService } from '../../core/i18n.service';

describe('LoginPage', () => {
  const auth = { login: vi.fn() };
  const api = { info: vi.fn(() => of({ mode: 'demo', model: 'scripted-offline', description: '', languages: [], tools: [], deskUrl: '' })) };

  beforeEach(() => {
    auth.login.mockReset();
    TestBed.configureTestingModule({
      providers: [provideRouter([]), { provide: AuthService, useValue: auth }, { provide: CopilotApiService, useValue: api }],
    });
    TestBed.inject(I18nService).setLang('en');
  });

  it('prefills a demo account, switches accounts and signs in', async () => {
    auth.login.mockReturnValue(of({ username: 'medina' }));
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    const fixture = TestBed.createComponent(LoginPage);
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('[data-testid=mode-badge]')).not.toBeNull();
    (el.querySelector('[data-testid=account-medina]') as HTMLButtonElement).click();
    await fixture.whenStable();
    (el.querySelector('[data-testid=sign-in]') as HTMLButtonElement).click();
    await fixture.whenStable();
    expect(auth.login).toHaveBeenCalledWith('medina', 'demo-pass-123');
    expect(navigate).toHaveBeenCalledWith(['/']);
  });

  it('shows an error for wrong credentials, in French too', async () => {
    auth.login.mockReturnValue(throwError(() => new Error('401')));
    const fixture = TestBed.createComponent(LoginPage);
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    const buttons = Array.from(el.querySelectorAll('button')).filter((b) => b.textContent?.trim() === 'fr');
    buttons[0].click();
    await fixture.whenStable();
    (el.querySelector('[data-testid=sign-in]') as HTMLButtonElement).click();
    await fixture.whenStable();
    expect(el.querySelector('[role=alert]')!.textContent).toContain('Identifiant ou mot de passe incorrect');
  });
});
