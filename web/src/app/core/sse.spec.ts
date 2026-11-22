import { SseParser, parseBlock } from './sse';

describe('SseParser', () => {
  it('parses Spring-style events (no space after the colon)', () => {
    const parser = new SseParser();
    const events = parser.feed('event:meta\ndata:{"conversationId":"c1","mode":"demo","model":"m","language":"en"}\n\n');
    expect(events).toEqual([{ type: 'meta', conversationId: 'c1', mode: 'demo', model: 'm', language: 'en' }]);
  });

  it('reassembles events split across chunks and CRLF line endings', () => {
    const parser = new SseParser();
    expect(parser.feed('event: token\r\nda')).toEqual([]);
    expect(parser.feed('ta: {"text":"Hel')).toEqual([]);
    expect(parser.feed('lo "}\r\n\r\nevent:token\ndata:{"text":"world"}\n\n')).toEqual([
      { type: 'token', text: 'Hello ' },
      { type: 'token', text: 'world' },
    ]);
  });

  it('flushes a final event without a trailing blank line', () => {
    const parser = new SseParser();
    parser.feed('event:done\ndata:{"answer":"x","durationMs":3,"conversationId":"c"}');
    expect(parser.flush()).toEqual([{ type: 'done', answer: 'x', durationMs: 3, conversationId: 'c' }]);
    expect(parser.flush()).toEqual([]);
  });

  it('ignores comments, empty blocks and invalid JSON', () => {
    expect(parseBlock(': keep-alive')).toBeNull();
    expect(parseBlock('event:token')).toBeNull();
    expect(parseBlock('event:token\ndata:{oops')).toBeNull();
    expect(parseBlock('data:{"text":"a"}')).toEqual({ type: 'message', text: 'a' });
  });
});
