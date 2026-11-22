import { Component, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { catchError, of } from 'rxjs';
import { AuthService } from '../../core/auth.service';
import { CopilotApiService } from '../../core/copilot-api.service';
import { I18nService } from '../../core/i18n.service';
import { Lang } from '../../core/models';
import { ModeBadge } from '../../shared/mode-badge';
import { Logo } from '../../shared/logo';
import { BRAND } from '../../brand/brand';
import { I18nKey } from '../../core/i18n.service';

interface DemoAccount {
  username: string;
  company: string;
  staff: boolean;
}

@Component({
  selector: 'app-login',
  imports: [FormsModule, ModeBadge, Logo],
  template: `
    <div class="grid min-h-dvh bg-bg lg:grid-cols-[minmax(0,1.05fr)_minmax(0,1fr)]">
      <aside class="relative hidden overflow-hidden bg-accent text-accent-fg lg:flex lg:flex-col lg:justify-between lg:p-12">
        <div aria-hidden="true" class="pointer-events-none absolute inset-0 opacity-[0.12] [background-image:radial-gradient(currentColor_1px,transparent_1px)] [background-size:22px_22px]"></div>
        <div aria-hidden="true" class="pointer-events-none absolute -bottom-40 -end-40 h-[28rem] w-[28rem] rounded-full bg-signal/30 blur-3xl"></div>
        <div class="relative flex items-center gap-2.5">
          <app-logo [size]="34" [onBrand]="true" />
          <span class="text-[17px] font-semibold tracking-tight">{{ brand.name }} <span class="font-normal opacity-80">{{ brand.product }}</span></span>
        </div>
        <div class="relative max-w-[460px]">
          <h2 class="text-[32px] font-semibold leading-[1.15] tracking-tight">{{ i18n.t('heroTitle') }}</h2>
          <p class="mt-4 text-[15px] leading-relaxed opacity-85">{{ i18n.t('heroBody') }}</p>
          <ul class="mt-7 space-y-2.5">
            @for (k of points; track k) {
              <li class="flex items-center gap-2.5 text-[14px]">
                <span class="grid size-5 place-items-center rounded-full bg-white/15"><svg viewBox="0 0 24 24" class="size-3" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" aria-hidden="true"><path d="m5 12 5 5L20 7"/></svg></span>{{ i18n.t(k) }}
              </li>
            }
          </ul>
        </div>
        <p class="relative text-[12px] opacity-70">{{ brand.suite }} · Dispatch · Settle · Copilot</p>
      </aside>

      <main class="relative flex items-center justify-center px-4 py-12">
        <div class="absolute end-4 top-4 flex rounded-lg bg-subtle p-0.5 text-xs font-semibold text-muted" role="group" aria-label="Language">
          @for (l of langs; track l) {
            <button type="button" (click)="i18n.setLang(l)" class="focus-ring rounded-md px-2.5 py-1 uppercase" [attr.aria-pressed]="i18n.lang() === l"
                    [class]="i18n.lang() === l ? 'bg-panel text-fg shadow-sm' : 'hover:text-fg'">{{ l }}</button>
          }
        </div>

        <section class="w-full max-w-[380px]">
          <span class="lg:hidden"><app-logo [size]="36" /></span>
          <h1 class="mt-4 text-[22px] font-semibold tracking-tight text-fg lg:mt-0">{{ i18n.t('signInTo') }} {{ brand.name }} {{ brand.product }}</h1>
          <p class="mt-1.5 text-[13.5px] text-muted">{{ i18n.t('tagline') }}</p>

          <form (ngSubmit)="submit()" class="mt-7 space-y-4">
            <label class="block">
              <span class="mb-1.5 block text-[12.5px] font-medium text-fg">{{ i18n.t('username') }}</span>
              <input name="username" [(ngModel)]="username" autocomplete="username" required
                     class="h-10 w-full rounded-lg border border-line bg-panel px-3 text-sm text-fg outline-none hover:border-faint/60 focus:border-accent focus:ring-2 focus:ring-accent/25" />
            </label>
            <label class="block">
              <span class="mb-1.5 block text-[12.5px] font-medium text-fg">{{ i18n.t('password') }}</span>
              <input name="password" type="password" [(ngModel)]="password" autocomplete="current-password" required
                     class="h-10 w-full rounded-lg border border-line bg-panel px-3 text-sm text-fg outline-none hover:border-faint/60 focus:border-accent focus:ring-2 focus:ring-accent/25" />
            </label>
            @if (error()) {
              <p class="rounded-lg bg-danger/10 px-3 py-2 text-sm text-danger" role="alert">{{ error() }}</p>
            }
            <button type="submit" [disabled]="loading()" data-testid="sign-in"
                    class="focus-ring h-10 w-full rounded-lg bg-accent px-4 text-sm font-semibold text-accent-fg shadow-sm transition hover:brightness-110 disabled:opacity-60">
              {{ loading() ? i18n.t('signingIn') : i18n.t('signIn') }}
            </button>
          </form>

          <div class="mt-6 rounded-xl border border-line bg-subtle/60 p-4">
            <p class="mb-2.5 text-[10.5px] font-semibold uppercase tracking-[0.08em] text-faint">{{ i18n.t('demoAccounts') }}</p>
            <div class="grid grid-cols-3 gap-2">
              @for (a of accounts; track a.username) {
                <button type="button" (click)="username = a.username" [attr.data-testid]="'account-' + a.username" [attr.aria-pressed]="username === a.username"
                        class="focus-ring rounded-lg border px-2 py-1.5 text-start transition"
                        [class]="username === a.username ? 'border-accent/60 bg-accent/10' : 'border-line bg-panel hover:border-faint/60'">
                  <span class="block truncate text-[12.5px] font-medium text-fg">{{ a.staff ? i18n.t('staff') : a.company }}</span>
                  <span class="block font-mono text-[11px] text-faint">{{ a.username }}</span>
                </button>
              }
            </div>
            <p class="mt-2.5 text-[12px] text-muted">{{ i18n.t('sampleHint') }}</p>
          </div>

          <div class="mt-5 flex justify-center"><app-mode-badge [info]="info()" /></div>
        </section>
      </main>
    </div>
  `,
})
export class LoginPage {
  protected readonly i18n = inject(I18nService);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  protected readonly info = toSignal(inject(CopilotApiService).info().pipe(catchError(() => of(undefined))));

  protected readonly langs: Lang[] = ['en', 'fr'];
  protected readonly brand = BRAND;
  protected readonly points: I18nKey[] = ['point1', 'point2', 'point3'];
  protected readonly accounts: DemoAccount[] = [
    { username: 'atlas', company: 'Atlas Home', staff: false },
    { username: 'medina', company: 'Medina Style', staff: false },
    { username: 'ops', company: '', staff: true },
  ];
  protected username = 'atlas';
  protected password = 'demo-pass-123';
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);

  submit(): void {
    this.loading.set(true);
    this.error.set(null);
    this.auth.login(this.username.trim(), this.password).subscribe({
      next: () => {
        this.loading.set(false);
        void this.router.navigate(['/']);
      },
      error: () => {
        this.loading.set(false);
        this.error.set(this.i18n.t('loginFailed'));
      },
    });
  }
}
