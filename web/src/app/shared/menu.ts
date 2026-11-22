import { Directive, ElementRef, HostListener, inject, signal } from '@angular/core';

/**
 * Open/close state for a small dropdown: closes on Escape and on clicks outside the host element.
 * Usage: <div appMenu #m="appMenu"> <button (click)="m.toggle()" [attr.aria-expanded]="m.open()"> ...
 */
@Directive({ selector: '[appMenu]', exportAs: 'appMenu' })
export class MenuState {
  private readonly host: ElementRef<HTMLElement> = inject(ElementRef);
  readonly open = signal(false);

  toggle(): void {
    this.open.update((o) => !o);
    if (this.open()) {
      queueMicrotask(() => this.host.nativeElement.querySelector<HTMLElement>('[role=menuitem]')?.focus());
    }
  }

  close(): void {
    this.open.set(false);
  }

  @HostListener('document:mousedown', ['$event'])
  onDocument(event: MouseEvent): void {
    if (this.open() && !this.host.nativeElement.contains(event.target as Node)) {
      this.close();
    }
  }

  @HostListener('document:keydown.escape')
  onEscape(): void {
    if (this.open()) {
      this.close();
      this.host.nativeElement.querySelector<HTMLElement>('[aria-haspopup]')?.focus();
    }
  }
}
