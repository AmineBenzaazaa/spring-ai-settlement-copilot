package com.benzaazaa.copilot.demo;

import java.math.BigDecimal;

/**
 * The planner's reading of a question: an intent plus the entities it extracted. {@code subject}
 * is the text used to recognise a courier name (the current question, even for follow-ups).
 */
public record Plan(Intent intent, Long statementId, String status, BigDecimal minAmount, BigDecimal maxAmount,
                   String city, String text, String subject) {

    public static Plan of(Intent intent, String subject) {
        return new Plan(intent, null, null, null, null, null, null, subject);
    }

    /** Intents answered without any tool call. */
    public boolean needsTools() {
        return switch (intent) {
            case HELP, FORECAST, OUT_OF_SCOPE -> false;
            default -> true;
        };
    }
}
