package com.msetransport.copilot.chat;

import com.msetransport.copilot.knowledge.TextNormalizer;
import java.util.Set;

/** English or French. Auto-detection is a word vote: cheap, deterministic and good on short questions. */
public enum Language {
    EN, FR;

    private static final Set<String> FRENCH = Set.of("quel", "quelle", "quels", "quelles", "combien", "pourquoi",
            "comment", "litige", "litiges", "livreur", "livreurs", "releve", "releves", "montre", "montrez", "affiche",
            "les", "des", "est", "sont", "mes", "mon", "ma", "nos", "doit", "plus", "ouverts", "ouvert", "commandes",
            "commande", "signifie", "bonjour", "salut", "qui", "quoi", "dois", "solde", "frais", "le", "la", "du",
            "et", "aide", "resume", "que", "veut", "dire", "explique", "pour", "donne", "moi", "ecart", "ecarts", "signale", "rapprochement", "transporteur");
    private static final Set<String> ENGLISH = Set.of("which", "what", "why", "how", "show", "the", "is", "are", "my",
            "me", "owes", "owe", "disputes", "dispute", "courier", "couriers", "statement", "statements", "orders",
            "order", "most", "over", "open", "does", "mean", "hello", "hi", "help", "and", "of", "list", "give",
            "balance", "flagged", "much", "many");

    public String code() {
        return name().toLowerCase();
    }

    public static Language resolve(String requested, String text) {
        if ("fr".equalsIgnoreCase(requested)) {
            return FR;
        }
        if ("en".equalsIgnoreCase(requested)) {
            return EN;
        }
        return detect(text);
    }

    public static Language detect(String text) {
        if (text == null) {
            return EN;
        }
        int fr = text.matches("(?s).*[éèêàçùâîôû].*") ? 2 : 0;
        int en = 0;
        // codes such as MISSING_FROM_STATEMENT are English in every language: do not let them vote
        String words = text.replaceAll("\\b[A-Z0-9]+(?:_[A-Z0-9]+)+\\b", " ");
        for (String word : TextNormalizer.fold(words).split("[^a-z]+")) {
            if (FRENCH.contains(word)) {
                fr++;
            }
            if (ENGLISH.contains(word)) {
                en++;
            }
        }
        return fr > en ? FR : EN;
    }
}
