import { Component, ElementRef, OnDestroy, effect, input, viewChild } from '@angular/core';
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

Chart.register(BarController, DoughnutController, BarElement, ArcElement, CategoryScale, LinearScale, Tooltip, Legend);

const PALETTE = ['#6366f1', '#d946ef', '#14b8a6', '#f59e0b', '#ef4444', '#0ea5e9'];

/** Renders the chart spec the server built from tool data (the model never draws numbers). */
@Component({
  selector: 'app-chart-card',
  template: `
    <figure class="mt-3 rounded-xl border border-slate-200 bg-slate-50/60 p-3" data-testid="chart">
      <figcaption class="mb-2 text-xs font-semibold uppercase tracking-wide text-slate-500">
        {{ spec().title }}@if (spec().unit) { <span class="font-normal normal-case"> ({{ spec().unit }})</span> }
      </figcaption>
      <div class="relative h-56"><canvas #canvas></canvas></div>
    </figure>
  `,
})
export class ChartCard implements OnDestroy {
  readonly spec = input.required<ChartSpec>();
  private readonly canvas = viewChild.required<ElementRef<HTMLCanvasElement>>('canvas');
  private chart?: Chart;

  constructor() {
    effect(() => {
      const spec = this.spec();
      this.chart?.destroy();
      this.chart = new Chart(this.canvas().nativeElement, {
        type: spec.type,
        data: {
          labels: spec.labels,
          datasets: spec.datasets.map((d, i) => ({
            label: d.label,
            data: d.data.map(Number),
            backgroundColor: spec.type === 'doughnut' ? PALETTE : PALETTE[i % PALETTE.length],
            borderRadius: spec.type === 'bar' ? 6 : 0,
            borderWidth: spec.type === 'doughnut' ? 2 : 0,
          })),
        },
        options: {
          responsive: true,
          maintainAspectRatio: false,
          animation: { duration: 500 },
          plugins: { legend: { display: spec.type === 'doughnut' || spec.datasets.length > 1, position: 'bottom' } },
          scales: spec.type === 'bar' ? { y: { beginAtZero: true, grid: { color: '#e2e8f0' } }, x: { grid: { display: false } } } : {},
        },
      });
    });
  }

  ngOnDestroy(): void {
    this.chart?.destroy();
  }
}
