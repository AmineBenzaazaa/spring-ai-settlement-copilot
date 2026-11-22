package com.msetransport.copilot.demo;

import com.msetransport.copilot.knowledge.TextNormalizer;
import java.math.BigDecimal;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic "planner" for the offline demo model: keyword rules (English and French) that
 * map common questions to an intent and its filters. It is intentionally simple and predictable:
 * its job is to make the demo and the tests reproducible without an API key, not to compete with
 * an LLM. Anything it does not recognise is refused instead of guessed.
 */
public class DemoPlanner {

    private static final Pattern STATEMENT_ID = Pattern.compile(
            "(?:statement|releve|stmt)\\s*(?:#|n°|nº|no\\.?|num(?:ero)?\\.?|number)?\\s*(\\d{1,9})\\b"
                    + "|(?:#|n°|nº)\\s*(\\d{1,9})\\b");
    private static final Pattern MIN_AMOUNT = Pattern.compile(
            "(?:over|above|more than|greater than|at least|>|plus de|superieurs? a|superieures? a|au-dessus de|depass\\w*|au moins)\\s*(\\d+(?:[.,]\\d{1,2})?)");
    private static final Pattern MAX_AMOUNT = Pattern.compile(
            "(?:under|below|less than|<|moins de|inferieurs? a|inferieures? a|en dessous de)\\s*(\\d+(?:[.,]\\d{1,2})?)");
    private static final Pattern TRACKING = Pattern.compile("\\b([a-z]{3}-[a-z0-9]{4,})\\b");
    private static final List<String> CITIES = List.of("casablanca", "rabat", "marrakech", "tangier", "tanger", "fes",
            "agadir", "oujda", "kenitra", "meknes", "tetouan");

    public Plan plan(String question, String previousQuestion) {
        String q = TextNormalizer.fold(question == null ? "" : question).trim();
        if (isFollowUp(q) && previousQuestion != null) {
            Plan previous = plan(previousQuestion, null);
            if (previous.needsTools()) {
                return new Plan(previous.intent(), previous.statementId(), previous.status(), previous.minAmount(),
                        previous.maxAmount(), previous.city(), previous.text(), question);
            }
        }
        return classify(q, question);
    }

    private Plan classify(String q, String original) {
        if (q.isBlank() || matches(q, "^(hi|hello|hey|bonjour|salut|help|aide|\\?)\\W*$")
                || matches(q, "what can you do|que (peux|pouvez)[- ](tu|vous) faire|how do i use|comment t'utiliser")) {
            return Plan.of(Intent.HELP, original);
        }
        if (matches(q, "next (month|week|year|quarter)|forecast|predict|will .{0,40}\\bowe\\b|tomorrow"
                + "|mois prochain|semaine prochaine|annee prochaine|prevision|prevoir|predire|devra")) {
            return Plan.of(Intent.FORECAST, original);
        }
        Long statementId = statementId(q);
        boolean disputeWord = matches(q, "disput|litige|reclamation|\\bclaim");
        if (statementId != null && !disputeWord
                && matches(q, "statement|releve|stmt|flag|signal|why|pourquoi|wrong|probleme|explain|expliqu")) {
            return new Plan(Intent.STATEMENT_EXPLAIN, statementId, null, null, null, null, null, original);
        }
        if (matches(q, "\\bmean|meaning|signifie|veut dire|definition|defini|\\bcomputed|calculated|calcule"
                + "|how does .{0,40}work|how do .{0,40}work|comment fonctionne|c'?est quoi|qu'?est[- ]ce qu"
                + "|what is an? |difference between|\\bthe rules?\\b|\\brules? (for|of|about)|regles? de|formula|formule")) {
            return new Plan(Intent.DOCS, null, null, null, null, null, original, original);
        }
        if (disputeWord) {
            return new Plan(Intent.DISPUTES, statementId, disputeStatus(q), amount(MIN_AMOUNT, q), null, null, null, original);
        }
        boolean courierWord = matches(q, "courier|livreur|transporteur|carrier");
        if ((courierWord && matches(q, "\\bmost|least|highest|biggest|largest|rank|compare|which|le plus|le moins"
                + "|classement|quel|compar|all|tous|each|chaque|balances|soldes"))
                || matches(q, "who owes|qui (me|nous) doit")) {
            return Plan.of(Intent.TOP_COURIER, original);
        }
        if (matches(q, "\\bowe|\\bowes|\\bowed|balance|solde|\\bdoit\\b|remit|collected|encaiss|reported")) {
            return Plan.of(Intent.COURIER_BALANCE, original);
        }
        if (matches(q, "statement|releve")) {
            return new Plan(Intent.STATEMENTS_LIST, null, statementStatus(q), null, null, null, null, original);
        }
        if (matches(q, "order|commande|parcel|colis|deliver|livre|shipment|envoi") || TRACKING.matcher(q).find()) {
            return new Plan(Intent.ORDERS, null, orderStatus(q), amount(MIN_AMOUNT, q), amount(MAX_AMOUNT, q), city(q),
                    tracking(q), original);
        }
        if (matches(q, "overview|summary|summar|resume|synthese|dashboard|kpi|how are we doing|how am i doing|status"
                + "|situation|total|bilan|\\bpoint\\b")) {
            return Plan.of(Intent.OVERVIEW, original);
        }
        return Plan.of(Intent.OUT_OF_SCOPE, original);
    }

    static boolean isFollowUp(String q) {
        return q.matches("^(and|et|what about|et pour|and for|same for|meme chose pour)\\b.*") && q.split("\\s+").length <= 6;
    }

    static Long statementId(String q) {
        Matcher m = STATEMENT_ID.matcher(q);
        if (!m.find()) {
            return null;
        }
        String id = m.group(1) != null ? m.group(1) : m.group(2);
        return Long.valueOf(id);
    }

    static BigDecimal amount(Pattern pattern, String q) {
        Matcher m = pattern.matcher(q);
        return m.find() ? new BigDecimal(m.group(1).replace(',', '.')) : null;
    }

    static String disputeStatus(String q) {
        if (matches(q, "\\bopen|ouvert|pending|en cours|en attente")) {
            return "OPEN";
        }
        if (matches(q, "resolved|resolu|accepted|accepte|paid|paye")) {
            return "RESOLVED";
        }
        if (matches(q, "rejected|rejete|refused|refuse")) {
            return "REJECTED";
        }
        return null;
    }

    static String statementStatus(String q) {
        if (matches(q, "reconciled|rapproche")) {
            return "RECONCILED";
        }
        if (matches(q, "failed|echoue|erreur")) {
            return "FAILED";
        }
        return null;
    }

    static String orderStatus(String q) {
        if (matches(q, "returned|retourn|refused|refuse")) {
            return "RETURNED";
        }
        if (matches(q, "delivered|livre")) {
            return "DELIVERED";
        }
        if (matches(q, "shipped|in transit|en transit|expedie")) {
            return "SHIPPED";
        }
        if (matches(q, "cancel|annul")) {
            return "CANCELLED";
        }
        return null;
    }

    static String city(String q) {
        for (String city : CITIES) {
            if (q.contains(city)) {
                String name = city.equals("tanger") ? "tangier" : city;
                return Character.toUpperCase(name.charAt(0)) + name.substring(1);
            }
        }
        return null;
    }

    static String tracking(String q) {
        Matcher m = TRACKING.matcher(q);
        return m.find() ? m.group(1).toUpperCase() : null;
    }

    private static boolean matches(String q, String regex) {
        return Pattern.compile(regex).matcher(q).find();
    }
}
