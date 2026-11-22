package com.msetransport.copilot.tools;

/**
 * A row (or document) an answer was built from, shown as a clickable chip in the UI.
 *
 * @param kind  statement, dispute, courier, order, overview or doc
 * @param url   deep link into the Settlement Desk; {@code null} for documentation snippets
 */
public record SourceRef(String kind, String id, String label, String url) {
}
