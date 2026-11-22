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

interface DemoAccount {
  username: string;
  company: string;
  staff: boolean;
}

@Component({
  selector: 'app-login',
  imports: [FormsModule, ModeBadge],
  template: `
    <main class="relative flex min-h-dvh items-center justify-center overflow-hidden bg-slate-950 px-4 py-10">
      <div class="pointer-events-none absolute -top-40 left-1/2 h-[36rem] w-[36rem] -translate-x-1/2 rounded-full bg-indigo-600/30 blur-3xl"></div>
      <div class="pointer-events-none absolute -bottom-40 right-0 h-96 w-96 rounded-full bg-fuchsia-600/20 blur-3xl"></div>

      <section class="relative w-full max-w-md">
        <div class="mb-6 flex items-center justify-between">
          <div class="flex items-center gap-3">
            <div class="grid size-10 place-items-center rounded-xl bg-gradient-to-br from-indigo-500 to-fuchsia-500 text-lg font-black text-white shadow-lg shadow-indigo-900/50">S</div>
            <div>
              <h1 class="text-lg font-bold tracking-tight text-white">Settlement Copilot</h1>
              <p class="text-xs text-slate-400">Spring AI · Angular</p>
            </div>
          </div>
          <div class="flex rounded-lg bg-white/10 p-0.5 text-xs font-semibold text-slate-300">
            @for (l of langs; track l) {
              <button type="button" (click)="i18n.setLang(l)" class="rounded-md px-2.5 py-1 uppercase"
                      [class]="i18n.lang() === l ? 'bg-white text-slate-900' : 'hover:text-white'">{{ l }}</button>
            }
          </div>
        </div>

        <form (ngSubmit)="submit()" class="rounded-2xl border border-white/10 bg-white/95 p-6 shadow-2xl shadow-black/40 backdrop-blur">
          <p class="mb-5 text-sm text-slate-600">{{ i18n.t('tagline') }}</p>

          <p class="mb-2 text-xs font-semibold uppercase tracking-wide text-slate-500">{{ i18n.t('demoAccounts') }}</p>
          <div class="mb-5 grid grid-cols-3 gap-2">
            @for (a of accounts; track a.username) {
              <button type="button" (click)="username = a.username" [attr.data-testid]="'account-' + a.username"
                      class="rounded-xl border px-2 py-2 text-left transition"
                      [class]="username === a.username ? 'border-indigo-500 bg-indigo-50 ring-2 ring-indigo-200' : 'border-slate-200 hover:border-slate-300'">
                <span class="block text-sm font-semibold text-slate-900">{{ a.username }}</span>
                <span class="block truncate text-[11px] text-slate-500">{{ a.staff ? i18n.t('staff') : a.company }}</span>
              </button>
            }
          </div>

          <label class="mb-3 block">
            <span class="mb-1 block text-xs font-medium text-slate-600">{{ i18n.t('username') }}</span>
            <input name="username" [(ngModel)]="username" autocomplete="username" required
                   class="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm outline-none focus:border-indigo-500 focus:ring-2 focus:ring-indigo-100" />
          </label>
          <label class="mb-4 block">
            <span class="mb-1 block text-xs font-medium text-slate-600">{{ i18n.t('password') }}</span>
            <input name="password" type="password" [(ngModel)]="password" autocomplete="current-password" required
                   class="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm outline-none focus:border-indigo-500 focus:ring-2 focus:ring-indigo-100" />
          </label>

          @if (error()) {
            <p class="mb-3 rounded-lg bg-rose-50 px-3 py-2 text-sm text-rose-700" role="alert">{{ error() }}</p>
          }

          <button type="submit" [disabled]="loading()" data-testid="sign-in"
                  class="w-full rounded-lg bg-gradient-to-r from-indigo-600 to-fuchsia-600 px-4 py-2.5 text-sm font-semibold text-white shadow-lg shadow-indigo-500/30 transition hover:brightness-110 disabled:opacity-60">
            {{ loading() ? i18n.t('signingIn') : i18n.t('signIn') }}
          </button>
          <p class="mt-3 text-center text-[11px] text-slate-400">Password for all demo accounts: <code>demo-pass-123</code></p>
        </form>

        <div class="mt-4 flex justify-center"><app-mode-badge [info]="info()" /></div>
      </section>
    </main>
  `,
})
export class LoginPage {
  protected readonly i18n = inject(I18nService);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  protected readonly info = toSignal(inject(CopilotApiService).info().pipe(catchError(() => of(undefined))));

  protected readonly langs: Lang[] = ['en', 'fr'];
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
