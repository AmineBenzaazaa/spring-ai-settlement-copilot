import { escapeHtml, renderMarkdown } from './markdown';

describe('renderMarkdown', () => {
  it('renders bold, italic, code, lists and paragraphs', () => {
    const html = renderMarkdown('**Rapido** owes *a lot* `MAD`\n\n1. one\n2. two\n- a\n- b\nend');
    expect(html).toBe(
      '<p><strong>Rapido</strong> owes <em>a lot</em> <code>MAD</code></p><ol><li>one</li><li>two</li></ol><ul><li>a</li><li>b</li></ul><p>end</p>',
    );
  });

  it('escapes HTML coming from the data', () => {
    expect(renderMarkdown('<img src=x onerror=alert(1)> **ok**')).toBe('<p>&lt;img src=x onerror=alert(1)&gt; <strong>ok</strong></p>');
    expect(escapeHtml(`"'&`)).toBe('&quot;&#39;&amp;');
  });

  it('handles empty text', () => {
    expect(renderMarkdown('')).toBe('');
  });
});
