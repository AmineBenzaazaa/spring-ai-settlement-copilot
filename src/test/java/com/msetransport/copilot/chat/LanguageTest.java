package com.benzaazaa.copilot.chat;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class LanguageTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "Which courier owes me the most?|EN",
            "Quel livreur me doit le plus ?|FR",
            "Montre les litiges ouverts|FR",
            "Que signifie MISSING_FROM_STATEMENT ?|FR",
            "Why was statement #1 flagged?|EN",
            "Pourquoi le relevé n°1 a-t-il été signalé ?|FR",
            "Speedex|EN"})
    void detectsTheQuestionLanguage(String text, Language expected) {
        assertThat(Language.detect(text)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource(value = {"fr,Hello,FR", "en,Bonjour,EN", "auto,Bonjour les livreurs,FR", ",Hello there,EN"})
    void anExplicitChoiceWins(String requested, String text, Language expected) {
        assertThat(Language.resolve(requested, text)).isEqualTo(expected);
        assertThat(Language.detect(null)).isEqualTo(Language.EN);
        assertThat(Language.FR.code()).isEqualTo("fr");
    }
}
