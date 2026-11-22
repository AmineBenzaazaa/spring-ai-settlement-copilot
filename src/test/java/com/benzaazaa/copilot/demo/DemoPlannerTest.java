package com.benzaazaa.copilot.demo;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class DemoPlannerTest {

    private final DemoPlanner planner = new DemoPlanner();

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource(delimiter = '|', value = {
            "Which courier owes me the most?|TOP_COURIER",
            "Quel livreur me doit le plus ?|TOP_COURIER",
            "Who owes me money?|TOP_COURIER",
            "How much does Speedex owe me?|COURIER_BALANCE",
            "Combien me doit Rapido ?|COURIER_BALANCE",
            "Show open disputes over 500 MAD|DISPUTES",
            "Litiges ouverts de plus de 400 MAD|DISPUTES",
            "Why was statement #12 flagged?|STATEMENT_EXPLAIN",
            "Pourquoi le relevé n°1 a-t-il été signalé ?|STATEMENT_EXPLAIN",
            "What does AMOUNT_MISMATCH mean?|DOCS",
            "How is the balance owed computed?|DOCS",
            "Que signifie MISSING_FROM_STATEMENT ?|DOCS",
            "List my statements|STATEMENTS_LIST",
            "Delivered orders in Casablanca over 300 MAD|ORDERS",
            "Find parcel SPX-100007|ORDERS",
            "Give me an overview|OVERVIEW",
            "Résumé de ma situation|OVERVIEW",
            "Which courier will owe me the most next month?|FORECAST",
            "Write me a poem|OUT_OF_SCOPE",
            "What's the weather in Rabat?|OUT_OF_SCOPE",
            "hello|HELP",
            "Que peux-tu faire ?|HELP"})
    void recognisesTheIntent(String question, Intent intent) {
        assertThat(planner.plan(question, null).intent()).isEqualTo(intent);
    }

    @Test
    void extractsFilters() {
        Plan disputes = planner.plan("Show open disputes over 500 MAD", null);
        assertThat(disputes.status()).isEqualTo("OPEN");
        assertThat(disputes.minAmount()).isEqualByComparingTo("500");

        Plan fr = planner.plan("litiges résolus dépassant 99,50 MAD sur le relevé 3", null);
        assertThat(fr.intent()).isEqualTo(Intent.DISPUTES);
        assertThat(fr.status()).isEqualTo("RESOLVED");
        assertThat(fr.minAmount()).isEqualByComparingTo(new BigDecimal("99.50"));
        assertThat(fr.statementId()).isEqualTo(3L);

        Plan orders = planner.plan("Returned orders in Tanger under 200", null);
        assertThat(orders.status()).isEqualTo("RETURNED");
        assertThat(orders.city()).isEqualTo("Tangier");
        assertThat(orders.maxAmount()).isEqualByComparingTo("200");

        assertThat(planner.plan("Find parcel SPX-100007", null).text()).isEqualTo("SPX-100007");
        assertThat(planner.plan("Why was statement #12 flagged?", null).statementId()).isEqualTo(12L);
        assertThat(planner.plan("rejected disputes", null).status()).isEqualTo("REJECTED");
        assertThat(planner.plan("failed statements", null).status()).isEqualTo("FAILED");
        assertThat(planner.plan("reconciled statements", null).status()).isEqualTo("RECONCILED");
        assertThat(planner.plan("shipped orders", null).status()).isEqualTo("SHIPPED");
        assertThat(planner.plan("cancelled orders", null).status()).isEqualTo("CANCELLED");
    }

    @Test
    void followUpsReuseThePreviousIntentWithTheNewSubject() {
        Plan plan = planner.plan("And Rapido?", "How much does Speedex owe me?");
        assertThat(plan.intent()).isEqualTo(Intent.COURIER_BALANCE);
        assertThat(plan.subject()).isEqualTo("And Rapido?");
        // a follow-up of a refusal is not inherited
        assertThat(planner.plan("and tomorrow?", "write a poem").intent()).isNotEqualTo(Intent.COURIER_BALANCE);
        assertThat(DemoPlanner.isFollowUp("and this is a very long sentence that is not a follow up")).isFalse();
    }

    @Test
    void blankQuestionIsHelp() {
        assertThat(planner.plan(null, null).intent()).isEqualTo(Intent.HELP);
        assertThat(Plan.of(Intent.HELP, "x").needsTools()).isFalse();
    }
}
