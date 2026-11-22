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
import { MessageBubble } from './message-bubble';
import { TracePanel } from './trace-panel';

@Component({
  selector: 'app-chat-page',
  imports: [FormsModule, MessageBubble, TracePanel, ModeBadge],
  template: `
    <div class="flex h-dvh flex-col bg-gradient-to-b from-slate-50 to-indigo-50/40">
      <header class="flex flex-wrap items-center gap-3 border-b border-slate-200 bg-white/80 px-4 py-2.5 backdrop-blur sm:px-6">
        <div class="flex items-center gap-2.5">
          <div class="grid size-8 place-items-center rounded-lg bg-gradient-to-br from-indigo-500 to-fuchsia-500 font-black text-white">S</div>
          <div class="leading-tight">
            <p class="text-sm font-bold tracking-tight text-slate-900">Settlement Copilot</p>
            <p class="hidden text-[11px] text-slate-500 sm:block">COD settlement · Spring AI</p>
          </div>
        </div>
        <app-mode-badge [info]="info()" />
        <div class="ml-auto flex items-center gap-2">
          <div class="flex rounded-lg bg-slate-100 p-0.5 text-xs font-semibold text-slate-500" data-testid="lang-toggle">
            @for (l of langs; track l) {
              <button type="button" (click)="i18n.setLang(l)" class="rounded-md px-2.5 py-1 uppercase"
                      [class]="i18n.lang() === l ? 'bg-white text-slate-900 shadow-sm' : 'hover:text-slate-800'" [attr.data-testid]="'lang-' + l">{{ l }}</button>
            }
          </div>
          <button type="button" (click)="store.reset()" class="hidden rounded-lg px-2.5 py-1.5 text-xs font-medium text-slate-600 hover:bg-slate-100 sm:block">{{ i18n.t('newChat') }}</button>
          @if (auth.user(); as u) {
            <span class="hidden items-center gap-2 rounded-lg bg-slate-100 px-2.5 py-1 text-xs text-slate-700 sm:inline-flex" data-testid="user-chip">
              <span class="font-semibold">{{ u.username }}</span>
              <span class="text-slate-400">{{ u.role === 'STAFF' ? i18n.t('staff') : i18n.t('merchant') + ' #' + u.merchantId }}</span>
            </span>
          }
          <button type="button" (click)="auth.logout()" class="rounded-lg px-2.5 py-1.5 text-xs font-medium text-slate-600 hover:bg-slate-100">{{ i18n.t('signOut') }}</button>
        </div>
      </header>

      <div class="mx-auto flex w-full max-w-6xl flex-1 gap-5 overflow-hidden px-4 py-4 sm:px-6">
        <main class="flex min-w-0 flex-1 flex-col">
          <div #thread class="flex-1 space-y-4 overflow-y-auto pb-4 pr-1" data-testid="thread">
            @if (!store.messages().length) {
              <div class="flex h-full flex-col items-center justify-center text-center">
                <div class="mb-4 grid size-14 place-items-center rounded-2xl bg-gradient-to-br from-indigo-500 to-fuchsia-500 text-2xl font-black text-white shadow-xl shadow-indigo-500/30">S</div>
                <h1 class="text-2xl font-bold tracking-tight text-slate-900">{{ i18n.t('welcome') }}</h1>
                <p class="mt-1 max-w-md text-sm text-slate-500">{{ i18n.t('welcomeSub') }}</p>
                <div class="mt-6 grid w-full max-w-2xl gap-2 sm:grid-cols-2">
                  @for (s of i18n.suggestions(); track s) {
                    <button type="button" (click)="ask(s)" data-testid="suggestion"
                            class="rounded-xl border border-slate-200 bg-white px-4 py-3 text-left text-sm text-slate-700 shadow-sm transition hover:-translate-y-0.5 hover:border-indigo-300 hover:shadow-md">
                      {{ s }}
                    </button>
                  }
                </div>
              </div>
            }
            @for (m of store.messages(); track m.id) {
              <app-message-bubble [message]="m" />
            }
          </div>

          <form (ngSubmit)="ask(draft())" class="mt-2 flex items-end gap-2 rounded-2xl border border-slate-200 bg-white p-2 shadow-lg shadow-slate-200/60">
            <textarea name="question" [ngModel]="draft()" (ngModelChange)="draft.set($event)" (keydown.enter)="onEnter($event)"
                      rows="1" maxlength="2000" [placeholder]="i18n.t('askPlaceholder')" data-testid="question"
                      class="max-h-32 min-h-10 flex-1 resize-none bg-transparent px-2 py-2 text-sm outline-none placeholder:text-slate-400"></textarea>
            @if (store.busy()) {
              <button type="button" (click)="store.stop()" class="rounded-xl bg-slate-200 px-4 py-2 text-sm font-semibold text-slate-700 hover:bg-slate-300">{{ i18n.t('stop') }}</button>
            } @else {
              <button type="submit" [disabled]="!draft().trim()" data-testid="send"
                      class="rounded-xl bg-gradient-to-r from-indigo-600 to-fuchsia-600 px-4 py-2 text-sm font-semibold text-white shadow-md shadow-indigo-500/30 transition hover:brightness-110 disabled:opacity-40">{{ i18n.t('send') }}</button>
            }
          </form>
        </main>

        <aside class="hidden w-80 shrink-0 space-y-4 overflow-y-auto lg:block">
          <app-trace-panel [message]="store.lastAssistant()" />
          @if (info(); as i) {
            <section class="rounded-2xl border border-slate-200 bg-white/80 p-4 text-[12px] text-slate-600 shadow-sm">
              <h2 class="mb-2 text-xs font-bold uppercase tracking-wider text-slate-500">{{ i.tools.length }} {{ i18n.t('tools') }}</h2>
              <div class="flex flex-wrap gap-1">
                @for (t of i.tools; track t) { <code class="rounded bg-slate-100 px-1.5 py-0.5 text-[11px]">{{ t }}</code> }
              </div>
            </section>
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
