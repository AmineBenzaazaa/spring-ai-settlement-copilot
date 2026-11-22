package com.msetransport.copilot.tools;

/**
 * One line of the tool-call trace panel: {@code getCourierBalances() -> 2 rows in 41 ms}.
 *
 * @param status  OK, NOT_FOUND, DENIED, UNAVAILABLE or ERROR
 * @param flagged number of free-text fields the injection guard neutralized in this result
 */
public record ToolCallTrace(int index, String tool, String arguments, String status, int rows, long durationMs,
                            int flagged) {
}
