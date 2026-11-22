import { Component, computed, inject, input } from '@angular/core';
import { CopilotInfo } from '../core/models';
import { I18nService } from '../core/i18n.service';

/** Always visible: which model answers. Amber for the offline demo model, violet for Claude. */
@Component({
  selector: 'app-mode-badge',
  template: `
    @if (info(); as i) {
      <span
        class="inline-flex items-center gap-2 rounded-full px-3 py-1 text-xs font-semibold ring-1"
        [class]="demo() ? 'bg-amber-50 text-amber-800 ring-amber-300' : 'bg-violet-50 text-violet-800 ring-violet-300'"
        [attr.title]="demo() ? i18n.t('demoModeHint') : i.description"
        data-testid="mode-badge"
      >
        <span class="relative flex size-2">
          <span class="absolute inline-flex size-full animate-ping rounded-full opacity-60" [class]="demo() ? 'bg-amber-400' : 'bg-violet-400'"></span>
          <span class="relative inline-flex size-2 rounded-full" [class]="demo() ? 'bg-amber-500' : 'bg-violet-500'"></span>
        </span>
        @if (demo()) {
          {{ i18n.t('demoMode') }} · {{ i18n.t('noKey') }}
        } @else {
          {{ i18n.t('claudeMode') }} · {{ i.model }}
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
