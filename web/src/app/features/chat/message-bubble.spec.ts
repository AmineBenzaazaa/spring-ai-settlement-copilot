import { TestBed } from '@angular/core/testing';
import { MessageBubble } from './message-bubble';
import { ChatMessage } from '../../core/models';

const base: ChatMessage = { id: 1, role: 'assistant', text: '', streaming: false, traces: [], sources: [] };

async function render(message: ChatMessage): Promise<HTMLElement> {
  const fixture = TestBed.createComponent(MessageBubble);
  fixture.componentRef.setInput('message', message);
  await fixture.whenStable();
  return fixture.nativeElement as HTMLElement;
}

describe('MessageBubble', () => {
  it('renders a user question', async () => {
    const el = await render({ ...base, role: 'user', text: 'Which courier?' });
    expect(el.querySelector('[data-testid=user-message]')!.textContent).toContain('Which courier?');
  });

  it('shows a working indicator with the current tool before the first token', async () => {
    const el = await render({ ...base, streaming: true, traces: [{ index: 1, tool: 'findCourier', arguments: '{}', status: 'OK', rows: 2, durationMs: 4, flagged: 0 }] });
    expect(el.textContent).toContain('findCourier()');
  });

  it('renders markdown, sources and the grounding badge when done', async () => {
    const el = await render({
      ...base,
      text: '**Speedex** owes 530.50 MAD',
      durationMs: 37,
      verification: { grounded: true, checkedAmounts: 1, ungrounded: [] },
      sources: [
        { kind: 'statement', id: '1', label: 'Statement #1', url: 'http://desk/statements/1' },
        { kind: 'doc', id: 'd', label: 'Glossary', url: null },
      ],
    });
    expect(el.querySelector('[data-testid=answer] strong')!.textContent).toBe('Speedex');
    const chips = el.querySelectorAll('[data-testid=source-chip]');
    expect(chips).toHaveLength(2);
    expect((chips[0] as HTMLAnchorElement).href).toBe('http://desk/statements/1');
    expect(el.querySelector('[data-testid=grounded]')!.textContent).toContain('1');
    expect(el.textContent).toContain('37 ms');
  });

  it('warns about ungrounded amounts and shows errors', async () => {
    const el = await render({ ...base, text: 'x', error: 'boom', verification: { grounded: false, checkedAmounts: 1, ungrounded: ['9.99'] } });
    expect(el.querySelector('[data-testid=ungrounded]')!.textContent).toContain('9.99');
    expect(el.querySelector('[role=alert]')!.textContent).toContain('boom');
  });

  it('shows a streaming caret and "no amounts" for amount-free answers', async () => {
    const streaming = await render({ ...base, text: 'partial', streaming: true });
    expect(streaming.querySelector('.caret')).not.toBeNull();
    const none = await render({ ...base, text: 'no data', verification: { grounded: true, checkedAmounts: 0, ungrounded: [] } });
    expect(none.querySelector('[data-testid=grounded]')!.textContent).toContain('No amounts');
  });
});
