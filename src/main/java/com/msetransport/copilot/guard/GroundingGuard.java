package com.benzaazaa.copilot.guard;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * "Refuse to guess", enforced after the fact: every money amount in the answer must appear in
 * the data the tools returned during this turn. A model that invents or miscopies a figure is
 * caught here and the UI shows a warning instead of a green "verified" badge.
 *
 * <p>Accepts both {@code 1234.50} and the French {@code 1 234,50}.
 */
@Component
public class GroundingGuard {

    /** 2-decimal amounts, optional thousands separators (space, narrow no-break space, comma or dot). */
    private static final Pattern AMOUNT = Pattern.compile(
            "(?<![\\d.,])\\d{1,3}(?:[ \\u00A0\\u202F,.]\\d{3})*[.,]\\d{2}(?![\\d])|(?<![\\d.,])\\d+[.,]\\d{2}(?![\\d])");

    public Verification check(String answer, List<String> toolPayloads) {
        Set<String> known = new HashSet<>();
        for (String payload : toolPayloads) {
            known.addAll(extract(payload));
        }
        Set<String> found = new LinkedHashSet<>(extract(answer == null ? "" : answer));
        List<String> ungrounded = new ArrayList<>();
        for (String amount : found) {
            if (!known.contains(amount) && !isZero(amount)) {
                ungrounded.add(amount);
            }
        }
        return new Verification(ungrounded.isEmpty(), found.size(), List.copyOf(ungrounded));
    }

    static Set<String> extract(String text) {
        Set<String> amounts = new LinkedHashSet<>();
        Matcher m = AMOUNT.matcher(text);
        while (m.find()) {
            amounts.add(normalize(m.group()));
        }
        return amounts;
    }

    /** {@code "1 234,50"} and {@code "1,234.50"} both become {@code "1234.50"}; a leading minus is ignored. */
    static String normalize(String raw) {
        String digits = raw.replaceAll("[ \\u00A0\\u202F]", "");
        int decimalSep = digits.length() - 3;
        String integer = digits.substring(0, decimalSep).replaceAll("[.,]", "");
        String normalized = integer.replaceFirst("^0+(?=\\d)", "") + "." + digits.substring(decimalSep + 1);
        return normalized;
    }

    private static boolean isZero(String amount) {
        return amount.matches("0+\\.00");
    }

    /**
     * @param grounded       every amount in the answer was found in the tool data
     * @param checkedAmounts how many distinct amounts the answer contained
     */
    public record Verification(boolean grounded, int checkedAmounts, List<String> ungrounded) {
    }
}
