import { Component, computed, inject, input } from '@angular/core';
import { CopilotInfo } from '../core/models';
import { I18nService } from '../core/i18n.service';

/** Always visible: which model answers. Amber for the offline demo model, brand colour for Claude. */
@Component({
  selector: 'app-mode-badge',
  template: `
    @if (info(); as i) {
      <span
        class="inline-flex items-center gap-1.5 rounded-full border px-2.5 py-0.5 text-[11.5px] font-medium"
        [class]="demo() ? 'border-amber-300/70 bg-amber-50 text-amber-800 dark:border-amber-400/30 dark:bg-amber-400/10 dark:text-amber-200' : 'border-accent/30 bg-accent/10 text-accent'"
        [attr.title]="demo() ? i18n.t('demoModeHint') : i.description"
        data-testid="mode-badge"
      >
        <span class="size-1.5 rounded-full" [class]="demo() ? 'bg-amber-500' : 'bg-accent'"></span>
        @if (demo()) {
          {{ i18n.t('demoMode') }} <span class="opacity-70">· {{ i18n.t('noKey') }}</span>
        } @else {
          {{ i18n.t('claudeMode') }} <span class="opacity-70">· {{ i.model }}</span>
        }
      </span>
    }
  `,
})
export class ModeBadge {
  readonly info = input<CopilotInfo | undefined>();
  protected readonly i18n = inject(I18nService);
  protected readonly demo = computed(() => this.info()?.mode === 'demo');
}
