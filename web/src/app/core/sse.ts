import { CopilotEvent } from './models';

/**
 * Incremental Server-Sent Events parser. EventSource cannot POST or send an Authorization header,
 * so the app reads the stream with fetch() and feeds the chunks here. Chunks can cut an event
 * anywhere; the parser keeps the unfinished tail until the blank line that ends the event arrives.
 */
export class SseParser {
  private buffer = '';

  feed(chunk: string): CopilotEvent[] {
    this.buffer += chunk.replace(/\r\n/g, '\n');
    const events: CopilotEvent[] = [];
    let end: number;
    while ((end = this.buffer.indexOf('\n\n')) >= 0) {
      const block = this.buffer.slice(0, end);
      this.buffer = this.buffer.slice(end + 2);
      const event = parseBlock(block);
      if (event) {
        events.push(event);
      }
    }
    return events;
  }

  /** Whatever is left when the stream closes without a trailing blank line. */
  flush(): CopilotEvent[] {
    const rest = this.buffer.trim();
    this.buffer = '';
    const event = rest ? parseBlock(rest) : null;
    return event ? [event] : [];
  }
}

export function parseBlock(block: string): CopilotEvent | null {
  let name = 'message';
  const data: string[] = [];
  for (const line of block.split('\n')) {
    if (line.startsWith(':')) {
      continue; // comment / keep-alive
    }
    const colon = line.indexOf(':');
    const field = colon < 0 ? line : line.slice(0, colon);
    let value = colon < 0 ? '' : line.slice(colon + 1);
    if (value.startsWith(' ')) {
      value = value.slice(1);
    }
    if (field === 'event') {
      name = value;
    } else if (field === 'data') {
      data.push(value);
    }
  }
  if (data.length === 0) {
    return null;
  }
  try {
    return { type: name, ...JSON.parse(data.join('\n')) } as CopilotEvent;
  } catch {
    return null;
  }
}
