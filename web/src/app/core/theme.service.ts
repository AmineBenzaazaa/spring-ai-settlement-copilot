import { Injectable, signal } from '@angular/core';

const KEY = 'mse-copilot.theme';

/** Light/dark theme: the saved choice, else the OS preference. Applied as the .dark class on <html>. */
@Injectable({ providedIn: 'root' })
export class ThemeService {
  readonly dark = signal(initial());

  constructor() {
    this.apply();
  }

  toggle(): void {
    this.dark.update((d) => !d);
    try {
      localStorage.setItem(KEY, this.dark() ? 'dark' : 'light');
    } catch {
      /* storage blocked */
    }
    this.apply();
  }

  private apply(): void {
    document.documentElement.classList.toggle('dark', this.dark());
  }
}

function initial(): boolean {
  try {
    const saved = localStorage.getItem(KEY);
    if (saved) {
      return saved === 'dark';
    }
  } catch {
    /* ignore */
  }
  return typeof matchMedia !== 'undefined' && matchMedia('(prefers-color-scheme: dark)').matches;
}
