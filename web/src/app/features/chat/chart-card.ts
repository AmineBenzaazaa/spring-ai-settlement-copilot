import { Component, ElementRef, OnDestroy, computed, effect, inject, input, viewChild } from '@angular/core';
import {
  ArcElement,
  BarController,
  BarElement,
  CategoryScale,
  Chart,
  DoughnutController,
  Legend,
  LinearScale,
  Tooltip,
} from 'chart.js';
import { ChartSpec } from '../../core/models';
import { I18nService } from '../../core/i18n.service';
import { ThemeService } from '../../core/theme.service';

Chart.register(BarController, DoughnutController, BarElement, ArcElement, CategoryScale, LinearScale, Tooltip, Legend);

/** Reads a brand token ("R G B" triplet) from :root, so charts follow the theme. */
function token(name: string, alpha = 1): string {
  const v = typeof getComputedStyle !== 'undefined' ? getComputedStyle(document.documentElement).getPropertyValue(`--${name}`).trim() : '';
  return v ? `rgb(${v} / ${alpha})` : '#888';
}

/** Outcomes keep their meaning everywhere: matched = success, mismatch = warn, missing = danger. */
const SEMANTIC: Record<string, string> = {
  MATCHED: 'success', AMOUNT_MISMATCH: 'warn', UNKNOWN_TRACKING: 'info', MISSING_FROM_STATEMENT: 'danger',
  OPEN: 'warn', RESOLVED: 'success', REJECTED: 'faint',
};
const SERIES = ['accent', 'signal', 'info', 'warn', 'danger', 'muted'];

/** Renders the chart spec the server built from tool data (the model never draws numbers). */
@Component({
  selector: 'app-chart-card',
  template: `
    <figure class="mt-3 rounded-xl border border-line bg-subtle/50 p-3" data-testid="chart">
      <figcaption class="mb-2 text-[10.5px] font-semibold uppercase tracking-[0.08em] text-muted">
        {{ spec().title }}@if (spec().unit) { <span class="font-normal normal-case"> ({{ spec().unit }})</span> }
      </figcaption>
      @if (compact()) {
        <!-- fewer than 3 points: a chart says nothing a number can't, so show the numbers -->
        <dl class="flex flex-wrap gap-3" data-testid="chart-summary">
          @for (row of rows(); track row.label) {
            <div class="min-w-36 rounded-lg border border-line bg-panel px-3 py-2">
              <dt class="text-[12px] text-muted">{{ row.label }}</dt>
              <dd class="mt-0.5 text-lg font-semibold tabular-nums text-fg">{{ row.value }}@if (spec().unit) { <span class="ms-1 text-[12px] font-normal text-muted">{{ spec().unit }}</span> }</dd>
            </div>
          }
        </dl>
      } @else {
        <div class="relative h-56"><canvas #canvas></canvas></div>
      }
    </figure>
  `,
})
export class ChartCard implements OnDestroy {
  readonly spec = input.required<ChartSpec>();
  private readonly canvas = viewChild<ElementRef<HTMLCanvasElement>>('canvas');
  protected readonly compact = computed(() => this.spec().labels.length < 3);
  protected readonly rows = computed(() => {
    const spec = this.spec();
    const fmt = new Intl.NumberFormat(this.i18n.lang() === 'fr' ? 'fr-FR' : 'en-US', spec.unit ? { minimumFractionDigits: 2, maximumFractionDigits: 2 } : {});
    return spec.labels.map((l, i) => ({ label: this.i18n.label(l), value: fmt.format(Number(spec.datasets[0]?.data[i] ?? 0)) }));
  });
  private chart?: Chart;
  private readonly i18n = inject(I18nService);
  private readonly theme = inject(ThemeService);

  constructor() {
    effect(() => {
      const spec = this.spec();
      this.i18n.lang(); // re-render legends when the language changes
      this.theme.dark(); // and colours when the theme changes (tokens are read below)
      const colours = (labels: string[]) => labels.map((l, i) => token(SEMANTIC[l] ?? SERIES[i % SERIES.length]));
      const ink = token('muted'), grid = token('line');
      this.chart?.destroy();
      const canvas = this.canvas();
      if (!canvas) {
        return;
      }
      this.chart = new Chart(canvas.nativeElement, {
        type: spec.type,
        data: {
          labels: spec.labels.map((l) => this.i18n.label(l)),
          datasets: spec.datasets.map((d, i) => ({
            label: d.label,
            data: d.data.map(Number),
            backgroundColor: spec.type === 'doughnut' ? colours(spec.labels) : token(SERIES[i % SERIES.length]),
            borderColor: token('panel'),
            borderRadius: spec.type === 'bar' ? 6 : 0,
            maxBarThickness: 56,
            borderWidth: spec.type === 'doughnut' ? 2 : 0,
          })),
        },
        options: {
          responsive: true,
          maintainAspectRatio: false,
          animation: { duration: 500 },
          plugins: {
            legend: { display: spec.type === 'doughnut' || spec.datasets.length > 1, position: 'bottom', labels: { color: ink, usePointStyle: true, pointStyle: 'circle', boxWidth: 8, padding: 14, font: { family: 'Inter', size: 12 } } },
          },
          scales: spec.type === 'bar' ? { y: { beginAtZero: true, grid: { color: grid }, ticks: { color: ink }, border: { display: false } }, x: { grid: { display: false }, ticks: { color: ink } } } : {},
          cutout: spec.type === 'doughnut' ? '62%' : undefined,
        },
      });
    });
  }

  ngOnDestroy(): void {
    this.chart?.destroy();
  }
}
