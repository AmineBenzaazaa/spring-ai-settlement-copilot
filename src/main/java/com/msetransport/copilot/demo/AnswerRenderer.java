package com.msetransport.copilot.demo;

import com.msetransport.copilot.chat.Language;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Templates the demo model's answers from the tools' JSON, in English or French. Every amount is
 * copied verbatim from the tool data (French only swaps the decimal point for a comma), which is
 * exactly what the grounding check expects from a real model too.
 */
public class AnswerRenderer {

    private static final int MAX_ITEMS = 8;
    private static final Pattern CODE = Pattern.compile("\\b[A-Z]{3,}(?:_[A-Z]+)+\\b");

    public String render(Plan plan, Language lang, List<NamedResult> results) {
        boolean fr = lang == Language.FR;
        if (!plan.needsTools()) {
            return switch (plan.intent()) {
                case HELP -> help(fr);
                case FORECAST -> fr
                        ? "Je ne peux pas prédire de montants futurs : les données de règlement ne couvrent que ce qui s'est déjà passé, et je ne devine pas. Je peux en revanche vous montrer ce que chaque livreur doit aujourd'hui."
                        : "I can't predict future amounts: the settlement data only covers what already happened, and I don't guess. I can show you what each courier owes today.";
                default -> fr
                        ? "Je réponds uniquement aux questions sur vos règlements contre remboursement (livreurs, relevés, litiges, commandes) et sur les règles de rapprochement. Je ne devine pas en dehors de ce périmètre."
                        : "I only answer questions about your cash-on-delivery settlement data (couriers, statements, disputes, orders) and how settlement works. I won't guess outside that.";
            };
        }
        if (results.isEmpty()) {
            return fr ? "Je n'ai reçu aucune donnée, je ne peux donc pas répondre sans deviner."
                    : "I received no data, so I can't answer without guessing.";
        }
        NamedResult last = results.getLast();
        if (last.json().has("error")) {
            return error(plan, last.json(), fr);
        }
        return switch (plan.intent()) {
            case TOP_COURIER -> ranking(last.json(), fr, false);
            case COURIER_BALANCE -> "getCourierBalances".equals(last.name())
                    ? ranking(last.json(), fr, true) : balance(last.json(), fr);
            case DISPUTES -> disputes(last.json(), fr);
            case STATEMENT_EXPLAIN -> statement(last.json(), fr);
            case STATEMENTS_LIST -> statements(last.json(), fr);
            case ORDERS -> orders(last.json(), fr);
            case OVERVIEW -> overview(last.json(), fr);
            case DOCS -> docs(plan.text(), last.json(), fr);
            default -> throw new IllegalStateException("No template for " + plan.intent());
        };
    }

    /** A tool's name and the JSON it returned. */
    public record NamedResult(String name, JsonNode json) {
    }

    // ---------------------------------------------------------------------------------------------

    private static String help(boolean fr) {
        return fr ? """
                Je suis votre copilote de règlement. Je lis vos données en direct (avec vos droits) et je cite mes sources. Exemples :
                - Quel livreur me doit le plus ?
                - Montre les litiges ouverts de plus de 400 MAD
                - Pourquoi le relevé n°1 a-t-il été signalé ?
                - Que signifie AMOUNT_MISMATCH ?""" : """
                I'm your settlement copilot. I read your live data (with your own permissions) and cite my sources. Try:
                - Which courier owes me the most?
                - Show open disputes over 400 MAD
                - Why was statement #1 flagged?
                - What does AMOUNT_MISMATCH mean?""";
    }

    private static String error(Plan plan, JsonNode json, boolean fr) {
        String code = json.path("error").asText();
        return switch (code) {
            case "NOT_FOUND" -> plan.intent() == Intent.STATEMENT_EXPLAIN
                    ? (fr ? "Je ne trouve pas le relevé n°%d dans votre compte. Il n'existe pas ou ne vous appartient pas, je ne vais donc pas deviner son contenu."
                          : "I couldn't find statement #%d in your account. It doesn't exist or isn't yours, so I won't guess what's in it.")
                            .formatted(plan.statementId())
                    : (fr ? "Je ne trouve pas cette donnée dans votre compte, et je ne vais pas deviner."
                          : "I couldn't find that in your account, and I won't guess.");
            case "UNAVAILABLE" -> fr
                    ? "La plateforme de règlement ne répond pas pour le moment, je ne peux donc pas donner de chiffres. Réessayez dans un instant."
                    : "The settlement platform isn't reachable right now, so I can't give you numbers. Please try again in a moment.";
            case "ACCESS_DENIED" -> fr
                    ? "La plateforme de règlement a refusé l'accès à ces données pour votre compte. Reconnectez-vous si votre session a expiré."
                    : "The settlement platform refused access to this data for your account. Sign in again if your session expired.";
            default -> fr ? "Je n'ai pas pu obtenir ces données, je préfère ne pas deviner."
                    : "I couldn't get that data, so I'd rather not guess.";
        };
    }

    private static String ranking(JsonNode json, boolean fr, boolean unmatchedCourier) {
        JsonNode couriers = json.path("couriers");
        if (couriers.isEmpty()) {
            return fr ? "Aucun livreur n'a de solde pour votre compte." : "No courier has a balance for your account.";
        }
        JsonNode top = couriers.get(0);
        StringBuilder sb = new StringBuilder();
        if (unmatchedCourier) {
            sb.append(fr ? "Je n'ai pas reconnu le livreur, voici donc tous vos livreurs.\n\n"
                    : "I couldn't tell which courier you meant, so here is every courier.\n\n");
        }
        sb.append((fr
                ? "**%s** vous doit le plus : **%s** encore à déclarer (%d commandes livrées, %s déclarés).\n\nClassement par solde dû :\n"
                : "**%s** owes you the most: **%s** still to be reported (%d delivered orders, %s reported so far).\n\nRanking by balance owed:\n")
                .formatted(top.path("name").asText(), mad(top.path("balanceOwed"), fr), top.path("deliveredOrders").asLong(),
                        mad(top.path("reportedCollections"), fr)));
        int i = 1;
        for (JsonNode c : couriers) {
            sb.append(i++).append(". ").append(c.path("name").asText()).append(colon(fr))
                    .append(mad(c.path("balanceOwed"), fr)).append('\n');
        }
        sb.append('\n').append(fr ? "Total encore dû par les livreurs : " : "Total still owed by couriers: ")
                .append(mad(json.path("totalOwed"), fr)).append('.');
        return sb.toString();
    }

    private static String balance(JsonNode b, boolean fr) {
        return (fr ? """
                **%s** vous doit encore **%s**.
                - Encaissements attendus (%d commandes livrées) : %s
                - Déclaré par le livreur : %s
                - Frais retenus : %s
                - Versé net : %s

                Solde dû = attendu − déclaré.""" : """
                **%s** still owes you **%s**.
                - Expected collections (%d delivered orders): %s
                - Reported by the courier: %s
                - Fees kept: %s
                - Net remitted to you: %s

                Balance owed = expected − reported.""").formatted(b.path("courierName").asText(), mad(b.path("balanceOwed"), fr),
                b.path("deliveredOrders").asLong(), mad(b.path("expectedCollections"), fr),
                mad(b.path("reportedCollections"), fr), mad(b.path("fees"), fr), mad(b.path("netRemitted"), fr));
    }

    private static String disputes(JsonNode d, boolean fr) {
        String status = d.path("status").isNull() || d.path("status").isMissingNode() ? null : d.path("status").asText();
        String what = disputeWord(status, fr, d.path("count").asInt() != 1);
        String over = d.hasNonNull("minAmount") ? (fr ? " de plus de " : " over ") + mad(d.path("minAmount"), fr) : "";
        if (d.path("count").asInt() == 0) {
            StringBuilder sb = new StringBuilder(fr
                    ? "Vous n'avez aucun %s%s.".formatted(disputeWord(status, true, false), over)
                    : "You have no %s%s.".formatted(disputeWord(status, false, true), over));
            if (d.hasNonNull("minAmount") && d.path("countIgnoringAmount").asInt() > 0) {
                sb.append((fr ? " Vous avez %d %s au total ; le plus important réclame %s."
                                : " You have %d %s in total; the largest claims %s.")
                        .formatted(d.path("countIgnoringAmount").asInt(),
                                disputeWord(status, fr, d.path("countIgnoringAmount").asInt() != 1),
                                mad(d.path("largestIgnoringAmount"), fr)));
            }
            return sb.toString();
        }
        StringBuilder sb = new StringBuilder((fr ? "Vous avez **%d %s**%s, pour un total réclamé de **%s** :\n"
                : "You have **%d %s**%s, claiming **%s** in total:\n")
                .formatted(d.path("count").asInt(), what, over, mad(d.path("totalClaimed"), fr)));
        int shown = 0;
        for (JsonNode x : d.path("disputes")) {
            if (shown++ == MAX_ITEMS) {
                break;
            }
            sb.append("- ").append(fr ? "Litige" : "Dispute").append(" #").append(x.path("id").asText())
                    .append(" · ").append(x.path("trackingNumber").asText())
                    .append(" (").append(outcome(x.path("outcome").asText(), fr))
                    .append(fr ? ", relevé n°" : ", statement #").append(x.path("statementId").asText()).append(")").append(colon(fr))
                    .append(mad(x.path("claimedAmount"), fr)).append(" — ").append(x.path("status").asText())
                    .append(fr ? " — « " : " — \"")
                    .append(x.path("reason").asText()).append(fr ? " »" : "\"").append('\n');
        }
        return sb.toString().stripTrailing();
    }

    private static String disputeWord(String status, boolean fr, boolean plural) {
        String base = fr ? (plural ? "litiges" : "litige") : (plural ? "disputes" : "dispute");
        if (status == null) {
            return base;
        }
        String adjective = switch (status) {
            case "OPEN" -> fr ? (plural ? "ouverts" : "ouvert") : "open";
            case "RESOLVED" -> fr ? (plural ? "résolus" : "résolu") : "resolved";
            case "REJECTED" -> fr ? (plural ? "rejetés" : "rejeté") : "rejected";
            default -> status.toLowerCase();
        };
        return fr ? base + " " + adjective : adjective + " " + base;
    }

    private static String statement(JsonNode s, boolean fr) {
        long id = s.path("statementId").asLong();
        if (!"RECONCILED".equals(s.path("status").asText())) {
            return (fr ? "Le relevé n°%d est au statut %s : il n'a pas encore de résultat de rapprochement, je ne peux donc pas l'expliquer."
                    : "Statement #%d is %s: it has no reconciliation result yet, so I can't explain it.")
                    .formatted(id, s.path("status").asText());
        }
        long total = 0;
        for (Iterator<Map.Entry<String, JsonNode>> it = s.path("counts").fields(); it.hasNext(); ) {
            total += it.next().getValue().asLong();
        }
        String period = s.path("periodStart").asText() + " → " + s.path("periodEnd").asText();
        String totals = (fr ? "Totaux : attendu %s, encaissé %s, frais %s, versé net %s, écart %s."
                : "Totals: expected %s, collected %s, fees %s, net remitted %s, difference %s.")
                .formatted(mad(s.path("expectedTotal"), fr), mad(s.path("collectedTotal"), fr), mad(s.path("feesTotal"), fr),
                        mad(s.path("netRemitted"), fr), mad(s.path("differenceTotal"), fr));
        if (!s.path("flagged").asBoolean()) {
            return (fr ? "Le relevé n°%d (livreur n°%s, %s) n'est pas signalé : ses %d lignes sont toutes rapprochées.\n\n%s"
                    : "Statement #%d (courier #%s, %s) is not flagged: all %d lines matched.\n\n%s")
                    .formatted(id, s.path("courierId").asText(), period, total, totals);
        }
        JsonNode issues = s.path("issues");
        StringBuilder sb = new StringBuilder((fr
                ? "Le **relevé n°%d** (livreur n°%s, %s) est signalé car **%d lignes sur %d** ne correspondent pas :\n"
                : "**Statement #%d** (courier #%s, %s) was flagged because **%d of its %d lines** did not match:\n")
                .formatted(id, s.path("courierId").asText(), period, issues.size(), total));
        for (JsonNode x : issues) {
            String tn = x.path("trackingNumber").asText();
            String line = switch (x.path("outcome").asText()) {
                case "AMOUNT_MISMATCH" -> (fr ? "%s · écart de montant : attendu %s, encaissé %s (écart %s)"
                        : "%s · amount mismatch: expected %s, collected %s (difference %s)")
                        .formatted(tn, mad(x.path("expectedAmount"), fr), mad(x.path("collectedAmount"), fr),
                                mad(x.path("difference"), fr));
                case "UNKNOWN_TRACKING" -> (fr ? "%s · numéro de suivi inconnu : le livreur déclare %s pour un colis que vous n'avez pas"
                        : "%s · unknown tracking number: the courier reported %s for a parcel you don't have")
                        .formatted(tn, mad(x.path("collectedAmount"), fr));
                case "MISSING_FROM_STATEMENT" -> (fr ? "%s · absent du relevé : livré, %s attendus mais non déclarés"
                        : "%s · missing from the statement: delivered, %s expected but not reported")
                        .formatted(tn, mad(x.path("expectedAmount"), fr));
                default -> tn + " · " + x.path("outcome").asText();
            };
            sb.append("- ").append(line);
            if (x.path("hasDispute").asBoolean()) {
                sb.append(fr ? " — litige ouvert" : " — disputed");
            }
            sb.append('\n');
        }
        return sb.append('\n').append(totals).toString();
    }

    private static String statements(JsonNode s, boolean fr) {
        int count = s.path("count").asInt();
        if (count == 0) {
            return fr ? "Vous n'avez encore aucun relevé." : "You have no statements yet.";
        }
        StringBuilder sb = new StringBuilder(fr
                ? "Vous avez **%d %s** :\n".formatted(count, count == 1 ? "relevé" : "relevés")
                : "You have **%d %s**:\n".formatted(count, count == 1 ? "statement" : "statements"));
        int shown = 0;
        for (JsonNode x : s.path("statements")) {
            if (shown++ == MAX_ITEMS) {
                break;
            }
            sb.append("- #").append(x.path("id").asText()).append(fr ? " · livreur n°" : " · courier #")
                    .append(x.path("courierId").asText()).append(" · ").append(x.path("periodStart").asText())
                    .append(" → ").append(x.path("periodEnd").asText()).append(" · ").append(x.path("status").asText())
                    .append(" · ").append(x.path("lineCount").asInt()).append(fr ? " lignes" : " lines").append('\n');
        }
        return sb.toString().stripTrailing();
    }

    private static String orders(JsonNode o, boolean fr) {
        long total = o.path("totalMatching").asLong();
        if (total == 0) {
            return fr ? "Aucune commande ne correspond à ces critères." : "No orders match these filters.";
        }
        StringBuilder sb = new StringBuilder((fr
                ? "J'ai trouvé **%d commandes** correspondantes (les %d plus importantes affichées, %s au total) :\n"
                : "I found **%d matching orders** (showing the %d largest, %s in total):\n")
                .formatted(total, o.path("shown").asInt(), mad(o.path("shownTotalAmount"), fr)));
        int shown = 0;
        for (JsonNode x : o.path("orders")) {
            if (shown++ == 5) {
                break;
            }
            sb.append("- ").append(x.path("trackingNumber").asText()).append(" · ").append(x.path("customerName").asText())
                    .append(" · ").append(x.path("city").asText()).append(" · ").append(mad(x.path("amountDue"), fr))
                    .append(" · ").append(x.path("status").asText()).append('\n');
        }
        return sb.toString().stripTrailing();
    }

    private static String overview(JsonNode o, boolean fr) {
        return (fr ? """
                Voici votre situation de règlement :
                - Encaissements attendus : %s
                - Déclarés par les livreurs : %s
                - Frais : %s
                - Versé net : %s
                - **Encore dû par les livreurs : %s**
                - Litiges ouverts : %d (%s réclamés)
                - Écarts détectés : %d (%s)""" : """
                Here is your settlement overview:
                - Expected COD collections: %s
                - Reported by couriers: %s
                - Fees: %s
                - Net remitted: %s
                - **Still owed by couriers: %s**
                - Open disputes: %d (%s claimed)
                - Discrepancies found: %d (%s)""").formatted(mad(o.path("expectedCollections"), fr),
                mad(o.path("reportedCollections"), fr), mad(o.path("fees"), fr), mad(o.path("netRemitted"), fr),
                mad(o.path("balanceOwed"), fr), o.path("openDisputes").asLong(), mad(o.path("openDisputedAmount"), fr),
                o.path("discrepancies").asLong(), mad(o.path("discrepancyAmount"), fr));
    }

    private static String docs(String question, JsonNode d, boolean fr) {
        JsonNode snippets = d.path("snippets");
        if (snippets.isEmpty()) {
            return fr ? "Je n'ai pas trouvé cela dans la documentation de règlement, je ne vais donc pas deviner."
                    : "I couldn't find that in the settlement documentation, so I won't guess.";
        }
        JsonNode best = snippets.get(0);
        String text = best.path("text").asText();
        Matcher code = CODE.matcher(question == null ? "" : question.toUpperCase());
        if (code.find()) {
            for (String line : text.split("\\n")) {
                String bullet = line.startsWith("- ") ? line.substring(2) : line;
                if (bullet.startsWith(code.group() + ":")) {
                    String definition = bullet.substring(code.group().length() + 1).strip();
                    return (fr ? "**%s** (d'après la documentation, *%s*, en anglais) : %s"
                            : "**%s** (from the documentation, *%s*): %s")
                            .formatted(code.group(), best.path("title").asText(), definition);
                }
            }
        }
        int newline = text.indexOf('\n');
        String body = (newline > 0 ? text.substring(newline + 1) : text).strip();
        List<String> paragraphs = new ArrayList<>(List.of(body.split("\\n\\n")));
        String excerpt = paragraphs.size() > 2 ? String.join("\n\n", paragraphs.subList(0, 2)) : body;
        return (fr ? "D'après la documentation (*%s*, en anglais) :\n\n%s" : "From the documentation (*%s*):\n\n%s")
                .formatted(best.path("title").asText(), excerpt);
    }

    private static String outcome(String outcome, boolean fr) {
        return switch (outcome) {
            case "AMOUNT_MISMATCH" -> fr ? "écart de montant" : "amount mismatch";
            case "UNKNOWN_TRACKING" -> fr ? "suivi inconnu" : "unknown tracking";
            case "MISSING_FROM_STATEMENT" -> fr ? "absent du relevé" : "missing from statement";
            default -> outcome;
        };
    }

    /** French typography puts a space before the colon. */
    private static String colon(boolean fr) {
        return fr ? " : " : ": ";
    }

    /** {@code "530.50"} becomes {@code "530.50 MAD"} (English) or {@code "530,50 MAD"} (French). */
    static String mad(JsonNode amount, boolean fr) {
        String value = amount == null || amount.isNull() || amount.isMissingNode() ? "0.00" : amount.asText();
        return (fr ? value.replace('.', ',') : value) + " MAD";
    }
}
