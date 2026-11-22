import { Component, ElementRef, effect, inject, signal, viewChild } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { catchError, of } from 'rxjs';
import { AuthService } from '../../core/auth.service';
import { ChatStore } from '../../core/chat.store';
import { CopilotApiService } from '../../core/copilot-api.service';
import { I18nService } from '../../core/i18n.service';
import { Lang } from '../../core/models';
import { ModeBadge } from '../../shared/mode-badge';
import { Logo } from '../../shared/logo';
import { MenuState } from '../../shared/menu';
import { ProductSwitcher } from '../../shared/product-switcher';
import { ThemeService } from '../../core/theme.service';
import { BRAND } from '../../brand/brand';
import { MessageBubble } from './message-bubble';
import { TracePanel } from './trace-panel';

@Component({
  selector: 'app-chat-page',
  imports: [FormsModule, MessageBubble, TracePanel, ModeBadge, Logo, MenuState, ProductSwitcher],
  template: `
    <a href="#question" class="sr-only focus:not-sr-only focus:absolute focus:start-2 focus:top-2 focus:z-50 focus:rounded-md focus:bg-panel focus:px-3 focus:py-2">{{ i18n.t('skip') }}</a>
    <div class="flex h-dvh flex-col bg-bg">
      <header class="flex flex-wrap items-center gap-2 border-b border-line bg-panel/85 px-3 py-2 backdrop-blur sm:gap-3 sm:px-5">
        <app-product-switcher [settleUrl]="info()?.deskUrl || undefined" />
        <span class="hidden h-5 w-px bg-line md:block" aria-hidden="true"></span>
        <app-mode-badge [info]="info()" />
        <div class="ms-auto flex items-center gap-1.5">
          <div class="flex rounded-lg bg-subtle p-0.5 text-xs font-semibold text-muted" data-testid="lang-toggle" role="group" aria-label="Language">
            @for (l of langs; track l) {
              <button type="button" (click)="i18n.setLang(l)" class="focus-ring rounded-md px-2.5 py-1 uppercase" [attr.aria-pressed]="i18n.lang() === l"
                      [class]="i18n.lang() === l ? 'bg-panel text-fg shadow-sm' : 'hover:text-fg'" [attr.data-testid]="'lang-' + l">{{ l }}</button>
            }
          </div>
          <button type="button" (click)="theme.toggle()" [attr.aria-label]="i18n.t('theme')" [attr.title]="i18n.t('theme')" data-testid="theme-toggle"
                  class="focus-ring grid size-8 place-items-center rounded-lg text-muted hover:bg-subtle hover:text-fg">
            @if (theme.dark()) {
              <svg viewBox="0 0 24 24" class="size-4" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" aria-hidden="true"><circle cx="12" cy="12" r="4"/><path d="M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4"/></svg>
            } @else {
              <svg viewBox="0 0 24 24" class="size-4" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" aria-hidden="true"><path d="M21 12.8A9 9 0 1 1 11.2 3a7 7 0 0 0 9.8 9.8z"/></svg>
            }
          </button>
          <button type="button" (click)="store.reset()" class="focus-ring hidden items-center gap-1.5 rounded-lg border border-line bg-panel px-2.5 py-1.5 text-xs font-medium text-fg hover:bg-subtle sm:inline-flex">
            <svg viewBox="0 0 24 24" class="size-3.5" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" aria-hidden="true"><path d="M12 5v14M5 12h14"/></svg>{{ i18n.t('newChat') }}
          </button>
          @if (auth.user(); as u) {
            <div appMenu #m="appMenu" class="relative">
              <button type="button" aria-haspopup="menu" [attr.aria-expanded]="m.open()" (click)="m.toggle()" [attr.aria-label]="i18n.t('account')" data-testid="user-chip"
                      class="focus-ring flex items-center gap-2 rounded-full border border-line bg-panel py-0.5 pe-3 ps-0.5 hover:bg-subtle">
                <span class="grid size-7 place-items-center rounded-full bg-accent/15 text-[11px] font-semibold uppercase text-accent">{{ u.username.slice(0, 2) }}</span>
                <span class="hidden text-start leading-tight sm:block">
                  <span class="block text-[12.5px] font-medium text-fg">{{ u.username }}</span>
                  <span class="block text-[10.5px] text-faint">{{ u.role === 'STAFF' ? i18n.t('staff') : i18n.t('merchant') + ' #' + u.merchantId }}</span>
                </span>
              </button>
              @if (m.open()) {
                <div role="menu" class="absolute end-0 top-full z-50 mt-2 w-56 rounded-xl border border-line bg-panel p-1.5 shadow-xl shadow-black/10">
                  <p class="border-b border-line px-2.5 pb-2 pt-1 text-[12px] text-muted"><span class="block text-[13px] font-medium text-fg">{{ u.username }}</span>{{ u.role === 'STAFF' ? i18n.t('staff') : i18n.t('merchant') + ' #' + u.merchantId }}</p>
                  <button type="button" role="menuitem" (click)="m.close(); auth.logout()" data-testid="sign-out"
                          class="mt-1.5 flex w-full items-center gap-2 rounded-lg px-2.5 py-2 text-start text-[13px] text-fg outline-none hover:bg-subtle focus-visible:bg-subtle">
                    <svg viewBox="0 0 24 24" class="size-4 text-muted" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" aria-hidden="true"><path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4M16 17l5-5-5-5M21 12H9"/></svg>{{ i18n.t('signOut') }}
                  </button>
                </div>
              }
            </div>
          }
        </div>
      </header>

      <div class="mx-auto flex w-full max-w-6xl flex-1 gap-5 overflow-hidden px-3 py-4 sm:px-6">
        <main class="flex min-w-0 flex-1 flex-col">
          <div #thread class="flex-1 space-y-4 overflow-y-auto pb-4 pe-1" data-testid="thread" aria-live="polite">
            @if (!store.messages().length) {
              <div class="flex h-full flex-col items-center justify-center text-center">
                <app-logo [size]="56" class="mb-5 drop-shadow-sm" />
                <h1 class="text-2xl font-semibold tracking-tight text-fg">{{ i18n.t('welcome') }}</h1>
                <p class="mt-1.5 max-w-md text-sm text-muted">{{ i18n.t('welcomeSub') }}</p>
                <div class="mt-7 grid w-full max-w-2xl gap-2 sm:grid-cols-2">
                  @for (s of i18n.suggestions(); track s) {
                    <button type="button" (click)="ask(s)" data-testid="suggestion"
                            class="focus-ring group flex items-center justify-between gap-3 rounded-xl border border-line bg-panel px-4 py-3 text-start text-sm text-fg shadow-sm transition hover:border-accent/50 hover:shadow-md">
                      {{ s }}
                      <svg viewBox="0 0 24 24" class="size-4 shrink-0 text-faint transition group-hover:translate-x-0.5 group-hover:text-accent rtl:rotate-180" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" aria-hidden="true"><path d="M5 12h14M13 6l6 6-6 6"/></svg>
                    </button>
                  }
                </div>
              </div>
            }
            @for (m of store.messages(); track m.id) {
              <app-message-bubble [message]="m" />
            }
          </div>

          <form (ngSubmit)="ask(draft())" class="mt-2 flex items-end gap-2 rounded-2xl border border-line bg-panel p-2 shadow-lg shadow-black/5 focus-within:border-accent/60">
            <label for="question" class="sr-only">{{ i18n.t('askPlaceholder') }}</label>
            <textarea id="question" name="question" [ngModel]="draft()" (ngModelChange)="draft.set($event)" (keydown.enter)="onEnter($event)"
                      rows="1" maxlength="2000" [placeholder]="i18n.t('askPlaceholder')" data-testid="question"
                      class="max-h-32 min-h-10 flex-1 resize-none bg-transparent px-2 py-2 text-sm text-fg outline-none placeholder:text-faint"></textarea>
            @if (store.busy()) {
              <button type="button" (click)="store.stop()" class="focus-ring rounded-xl bg-subtle px-4 py-2 text-sm font-semibold text-fg hover:bg-line">{{ i18n.t('stop') }}</button>
            } @else {
              <button type="submit" [disabled]="!draft().trim()" data-testid="send"
                      class="focus-ring inline-flex items-center gap-1.5 rounded-xl bg-accent px-4 py-2 text-sm font-semibold text-accent-fg shadow-sm transition hover:brightness-110 disabled:opacity-40">
                {{ i18n.t('send') }}
                <svg viewBox="0 0 24 24" class="size-4 rtl:rotate-180" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" aria-hidden="true"><path d="M5 12h14M13 6l6 6-6 6"/></svg>
              </button>
            }
          </form>
          <p class="mt-2 text-center text-[11px] text-faint">{{ brand.name }} {{ brand.product }} · {{ brand.suite }}</p>
        </main>

        <aside class="hidden w-80 shrink-0 space-y-4 overflow-y-auto lg:block">
          <app-trace-panel [message]="store.lastAssistant()" />
          @if (info(); as i) {
            <section class="rounded-2xl border border-line bg-panel p-4 text-[12px] text-muted shadow-sm">
              <h2 class="mb-2.5 text-[10.5px] font-semibold uppercase tracking-[0.08em] text-faint">{{ i18n.t('toolsTitle') }} · {{ i.tools.length }}</h2>
              <ul class="grid grid-cols-2 gap-1">
                @for (t of i.tools; track t) {
                  <li class="truncate rounded-md bg-subtle px-2 py-1 text-[11.5px] text-fg" [attr.title]="t">{{ i18n.tool(t) }}</li>
                }
              </ul>
            </section>
          } @else {
            <div class="space-y-2 rounded-2xl border border-line bg-panel p-4"><div class="skeleton h-3 w-24"></div><div class="skeleton h-6"></div><div class="skeleton h-6"></div></div>
          }
        </aside>
      </div>
    </div>
  `,
})
export class ChatPage {
  protected readonly store = inject(ChatStore);
  protected readonly auth = inject(AuthService);
  protected readonly i18n = inject(I18nService);
  protected readonly theme = inject(ThemeService);
  protected readonly brand = BRAND;
  protected readonly info = toSignal(inject(CopilotApiService).info().pipe(catchError(() => of(undefined))));
  protected readonly langs: Lang[] = ['en', 'fr'];
  protected readonly draft = signal('');
  private readonly thread = viewChild<ElementRef<HTMLElement>>('thread');

  constructor() {
    // keep the newest tokens in view while the answer streams
    effect(() => {
      this.store.messages();
      const el = this.thread()?.nativeElement;
      if (el) {
        queueMicrotask(() => (el.scrollTop = el.scrollHeight));
      }
    });
  }

  ask(question: string): void {
    if (!question.trim()) {
      return;
    }
    this.store.ask(question);
    this.draft.set('');
  }

  onEnter(event: Event): void {
    const keyboard = event as KeyboardEvent;
    if (!keyboard.shiftKey) {
      keyboard.preventDefault();
      this.ask(this.draft());
    }
  }
}
