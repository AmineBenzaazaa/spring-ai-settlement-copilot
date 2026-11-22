package com.msetransport.copilot.tools;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Money leaves the tools as strings with exactly two decimals ({@code "530.50"}), the same shape
 * the settlement API uses. A JSON number would lose the trailing zero ({@code 530.5}) and the
 * grounding check could no longer match the answer against the data.
 */
public final class Money {

    private Money() {
    }

    public static String format(BigDecimal amount) {
        return amount == null ? null : amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    public static BigDecimal orZero(BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO : amount;
    }
}
