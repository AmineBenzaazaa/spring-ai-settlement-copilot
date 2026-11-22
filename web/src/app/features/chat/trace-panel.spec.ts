import { TestBed } from '@angular/core/testing';
import { TracePanel } from './trace-panel';
import { ChatMessage, ToolCallTrace } from '../../core/models';

const trace = (index: number, tool: string, status: ToolCallTrace['status'], args = '{}', flagged = 0): ToolCallTrace =>
  ({ index, tool, arguments: args, status, rows: 2, durationMs: 12, flagged });

describe('TracePanel', () => {
  it('is empty before the first question', async () => {
    const fixture = TestBed.createComponent(TracePanel);
    await fixture.whenStable();
    expect(fixture.nativeElement.textContent).toContain('Tool calls appear here');
  });

  it('lists each tool call with arguments, status, rows and timing', async () => {
    const fixture = TestBed.createComponent(TracePanel);
    const message: ChatMessage = {
      id: 2, role: 'assistant', text: '', streaming: true, sources: [],
      traces: [trace(1, 'findCourier', 'OK', '{"query":""}'), trace(2, 'getStatementReconciliation', 'NOT_FOUND', '{"statementId":12}'),
        trace(3, 'listDisputes', 'UNAVAILABLE', 'not-json', 1)],
      verification: { grounded: false, checkedAmounts: 1, ungrounded: ['1.00'] },
    };
    fixture.componentRef.setInput('message', message);
    await fixture.whenStable();
    const items = fixture.nativeElement.querySelectorAll('[data-testid=trace-item]');
    expect(items).toHaveLength(3);
    expect(items[0].textContent).toContain('findCourier()');
    expect(items[1].textContent).toContain('statementId: 12');
    expect(items[1].textContent).toContain('Not found');
    expect(items[1].textContent).toContain('Statement reconciliation');
    expect(items[2].textContent).toContain('not-json');
    expect(items[2].textContent).toContain('1 suspicious text neutralized');
    expect(fixture.nativeElement.textContent).toContain('live');
    expect(fixture.nativeElement.textContent).toContain('Not grounded');
  });
});
