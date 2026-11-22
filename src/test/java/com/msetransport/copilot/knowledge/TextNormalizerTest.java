package com.msetransport.copilot.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TextNormalizerTest {

    @Test
    void foldsAccentsAndCase() {
        assertThat(TextNormalizer.fold("Relevé Écart")).isEqualTo("releve ecart");
    }

    @Test
    void mapsFrenchDomainWordsToEnglishAndDropsStopWords() {
        assertThat(TextNormalizer.terms("Que signifie un litige sur le relevé ?"))
                .containsExactly("dispute", "statement");
    }

    @Test
    void splitsCodesAndCamelCase() {
        assertThat(TextNormalizer.terms("AMOUNT_MISMATCH")).containsExactly("amount_mismatch", "amount", "mismatch");
        assertThat(TextNormalizer.terms("balanceOwed")).containsExactly("balance", "owed");
    }

    @Test
    void stemsCommonSuffixes() {
        assertThat(TextNormalizer.stem("disputes")).isEqualTo("dispute");
        assertThat(TextNormalizer.stem("discrepancies")).isEqualTo("discrepancy");
        assertThat(TextNormalizer.stem("matching")).isEqualTo("match");
        assertThat(TextNormalizer.stem("reported")).isEqualTo("report");
        assertThat(TextNormalizer.stem("class")).isEqualTo("class");
        assertThat(TextNormalizer.terms(null)).isEmpty();
    }
}
