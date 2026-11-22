package com.msetransport.copilot.knowledge;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Turns English or French text into comparable terms: lower case, accents removed, stop words
 * dropped, French domain words mapped to their English term ("litige" becomes "dispute"), and a
 * light plural/suffix stemmer. Good enough for a few pages of documentation, fully deterministic.
 */
public final class TextNormalizer {

    private static final Set<String> STOP_WORDS = Set.of(
            "the", "a", "an", "of", "to", "in", "on", "for", "and", "or", "is", "are", "was", "be", "it", "this",
            "that", "with", "by", "as", "at", "from", "what", "how", "why", "does", "do", "my", "me", "i", "we",
            "you", "your", "our", "can", "which", "when", "who", "mean", "means",
            "le", "la", "les", "un", "une", "des", "du", "de", "d", "l", "et", "ou", "est", "sont", "en", "au", "aux",
            "pour", "par", "sur", "avec", "ce", "cette", "ces", "que", "qui", "quoi", "comment", "pourquoi", "mon",
            "ma", "mes", "je", "nous", "vous", "il", "elle", "signifie", "veut", "dire", "quel", "quelle");

    /** French domain vocabulary to the English term used in the docs (accents already removed). */
    private static final Map<String, String> SYNONYMS = Map.ofEntries(
            Map.entry("litige", "dispute"), Map.entry("litiges", "dispute"), Map.entry("reclamation", "dispute"),
            Map.entry("livreur", "courier"), Map.entry("livreurs", "courier"), Map.entry("transporteur", "courier"),
            Map.entry("releve", "statement"), Map.entry("releves", "statement"),
            Map.entry("rapprochement", "reconciliation"), Map.entry("ecart", "discrepancy"), Map.entry("ecarts", "discrepancy"),
            Map.entry("solde", "balance"), Map.entry("frais", "fee"), Map.entry("commande", "order"),
            Map.entry("commandes", "order"), Map.entry("encaisse", "collected"), Map.entry("encaissement", "collected"),
            Map.entry("manquant", "missing"), Map.entry("manquante", "missing"), Map.entry("inconnu", "unknown"),
            Map.entry("montant", "amount"), Map.entry("livre", "delivered"), Map.entry("livree", "delivered"),
            Map.entry("retourne", "returned"), Map.entry("doublon", "duplicate"), Map.entry("fichier", "file"),
            Map.entry("periode", "period"), Map.entry("resolu", "resolved"), Map.entry("rejete", "rejected"),
            Map.entry("ouvert", "open"), Map.entry("verse", "remitted"), Map.entry("virement", "remitted"),
            Map.entry("du", "owed"), Map.entry("doit", "owe"), Map.entry("devoir", "owe"),
            Map.entry("computed", "formula"), Map.entry("compute", "formula"), Map.entry("calculated", "formula"),
            Map.entry("calculate", "formula"), Map.entry("calcule", "formula"), Map.entry("calcul", "formula"),
            Map.entry("formulas", "formula"), Map.entry("formule", "formula"));

    private TextNormalizer() {
    }

    public static String fold(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT);
    }

    public static List<String> terms(String text) {
        List<String> terms = new ArrayList<>();
        if (text == null) {
            return terms;
        }
        // balanceOwed -> balance Owed, so formulas written in camelCase are searchable by word
        String spaced = text.replaceAll("([a-z])([A-Z])", "$1 $2");
        for (String raw : fold(spaced).split("[^a-z0-9_]+")) {
            if (raw.isEmpty() || STOP_WORDS.contains(raw)) {
                continue;
            }
            // AMOUNT_MISMATCH -> amount, mismatch (and keep the full token too)
            if (raw.contains("_")) {
                terms.add(raw);
                for (String part : raw.split("_")) {
                    if (!part.isEmpty()) {
                        terms.add(stem(SYNONYMS.getOrDefault(part, part)));
                    }
                }
                continue;
            }
            terms.add(stem(SYNONYMS.getOrDefault(raw, raw)));
        }
        return terms;
    }

    static String stem(String word) {
        if (word.length() > 5 && word.endsWith("ies")) {
            return word.substring(0, word.length() - 3) + "y";
        }
        if (word.length() > 5 && word.endsWith("ing")) {
            return word.substring(0, word.length() - 3);
        }
        if (word.length() > 4 && word.endsWith("ed")) {
            return word.substring(0, word.length() - 2);
        }
        if (word.length() > 3 && word.endsWith("s") && !word.endsWith("ss")) {
            return word.substring(0, word.length() - 1);
        }
        return word;
    }
}
