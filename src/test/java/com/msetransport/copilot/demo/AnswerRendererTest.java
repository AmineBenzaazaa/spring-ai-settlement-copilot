package com.msetransport.copilot.demo;

import static org.assertj.core.api.Assertions.assertThat;

import com.msetransport.copilot.chat.Language;
import com.msetransport.copilot.demo.AnswerRenderer.NamedResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class AnswerRendererTest {

    private final AnswerRenderer renderer = new AnswerRenderer();
    private final ObjectMapper mapper = new ObjectMapper();

    private NamedResult result(String tool, String json) throws Exception {
        return new NamedResult(tool, mapper.readTree(json));
    }

    private static Plan plan(Intent intent) {
        return Plan.of(intent, "q");
    }

    @Test
    void rankingInEnglishAndFrench() throws Exception {
        NamedResult r = result("getCourierBalances", """
                {"currency":"MAD","totalOwed":"4845.50","couriers":[
                 {"name":"Rapido Express","deliveredOrders":9,"reportedCollections":"0.00","balanceOwed":"4315.00"},
                 {"name":"Speedex Colis","deliveredOrders":8,"reportedCollections":"4057.50","balanceOwed":"530.50"}]}""");
        String en = renderer.render(plan(Intent.TOP_COURIER), Language.EN, List.of(r));
        assertThat(en).startsWith("**Rapido Express** owes you the most: **4315.00 MAD**")
                .contains("2. Speedex Colis: 530.50 MAD").contains("Total still owed by couriers: 4845.50 MAD.");
        String fr = renderer.render(plan(Intent.TOP_COURIER), Language.FR, List.of(r));
        assertThat(fr).contains("vous doit le plus : **4315,00 MAD**").contains("2. Speedex Colis : 530,50 MAD");
        String fallback = renderer.render(plan(Intent.COURIER_BALANCE), Language.EN, List.of(r));
        assertThat(fallback).startsWith("I couldn't tell which courier you meant");
        assertThat(renderer.render(plan(Intent.TOP_COURIER), Language.EN,
                List.of(result("getCourierBalances", "{\"couriers\":[]}")))).contains("No courier");
    }

    @Test
    void courierBalance() throws Exception {
        NamedResult r = result("getCourierBalance", """
                {"courierName":"Speedex Colis","deliveredOrders":8,"expectedCollections":"4588.00",
                 "reportedCollections":"4057.50","fees":"225.00","netRemitted":"3832.50","balanceOwed":"530.50"}""");
        assertThat(renderer.render(plan(Intent.COURIER_BALANCE), Language.EN, List.of(r)))
                .startsWith("**Speedex Colis** still owes you **530.50 MAD**").contains("Fees kept: 225.00 MAD");
        assertThat(renderer.render(plan(Intent.COURIER_BALANCE), Language.FR, List.of(r))).contains("Frais retenus : 225,00 MAD");
    }

    @Test
    void disputesWithAndWithoutMatches() throws Exception {
        NamedResult none = result("listDisputes", """
                {"status":"OPEN","minAmount":"500.00","count":0,"totalClaimed":"0.00","largestClaimed":null,
                 "countIgnoringAmount":2,"largestIgnoringAmount":"480.50","disputes":[]}""");
        assertThat(renderer.render(plan(Intent.DISPUTES), Language.EN, List.of(none)))
                .isEqualTo("You have no open disputes over 500.00 MAD. You have 2 open disputes in total; the largest claims 480.50 MAD.");
        assertThat(renderer.render(plan(Intent.DISPUTES), Language.FR, List.of(none)))
                .startsWith("Vous n'avez aucun litige ouvert de plus de 500,00 MAD.");
        NamedResult some = result("listDisputes", """
                {"status":null,"minAmount":null,"count":1,"totalClaimed":"50.00","largestClaimed":"50.00",
                 "countIgnoringAmount":1,"largestIgnoringAmount":"50.00","disputes":[{"id":1,"statementId":1,
                 "trackingNumber":"SPX-100007","outcome":"AMOUNT_MISMATCH","claimedAmount":"50.00","status":"OPEN","reason":"short"}]}""");
        assertThat(renderer.render(plan(Intent.DISPUTES), Language.EN, List.of(some)))
                .contains("**1 dispute**").contains("Dispute #1 · SPX-100007 (amount mismatch, statement #1): 50.00 MAD — OPEN — \"short\"");
        assertThat(renderer.render(plan(Intent.DISPUTES), Language.FR, List.of(some))).contains("« short »");
        NamedResult resolved = result("listDisputes", """
                {"status":"RESOLVED","count":0,"countIgnoringAmount":0,"disputes":[]}""");
        assertThat(renderer.render(plan(Intent.DISPUTES), Language.FR, List.of(resolved))).contains("litige résolu");
    }

    @Test
    void statementFlaggedCleanAndPending() throws Exception {
        String base = """
                {"statementId":1,"courierId":1,"periodStart":"2026-10-01","periodEnd":"2026-10-31","status":"%s",
                 "counts":{"MATCHED":11,"AMOUNT_MISMATCH":1,"UNKNOWN_TRACKING":1,"MISSING_FROM_STATEMENT":1},"flagged":%s,
                 "expectedTotal":"4588.00","collectedTotal":"4256.50","feesTotal":"250.00","netRemitted":"4006.50",
                 "differenceTotal":"-331.50","issues":%s}""";
        String issues = """
                [{"outcome":"AMOUNT_MISMATCH","trackingNumber":"SPX-100007","expectedAmount":"867.50","collectedAmount":"817.50","difference":"-50.00","hasDispute":true},
                 {"outcome":"UNKNOWN_TRACKING","trackingNumber":"SPX-UNKNOWN01","expectedAmount":"0.00","collectedAmount":"199.00","difference":"199.00","hasDispute":false},
                 {"outcome":"MISSING_FROM_STATEMENT","trackingNumber":"SPX-100001","expectedAmount":"480.50","collectedAmount":"0.00","difference":"-480.50","hasDispute":false},
                 {"outcome":"OTHER","trackingNumber":"X"}]""";
        Plan plan = new Plan(Intent.STATEMENT_EXPLAIN, 1L, null, null, null, null, null, "q");
        String en = renderer.render(plan, Language.EN, List.of(result("getStatementReconciliation", base.formatted("RECONCILED", true, issues))));
        assertThat(en).contains("**4 of its 14 lines**").contains("expected 867.50 MAD, collected 817.50 MAD (difference -50.00 MAD) — disputed")
                .contains("reported 199.00 MAD for a parcel you don't have").contains("480.50 MAD expected but not reported")
                .contains("difference -331.50 MAD.");
        String fr = renderer.render(plan, Language.FR, List.of(result("x", base.formatted("RECONCILED", true, issues))));
        assertThat(fr).contains("**4 lignes sur 14**").contains("litige ouvert");
        assertThat(renderer.render(plan, Language.EN, List.of(result("x", base.formatted("RECONCILED", false, "[]")))))
                .contains("is not flagged: all 14 lines matched");
        assertThat(renderer.render(plan, Language.FR, List.of(result("x", base.formatted("RECONCILED", false, "[]")))))
                .contains("n'est pas signalé");
        assertThat(renderer.render(plan, Language.EN, List.of(result("x", base.formatted("PROCESSING", false, "[]")))))
                .startsWith("Statement #1 is PROCESSING");
    }

    @Test
    void statementsOrdersOverviewAndDocs() throws Exception {
        assertThat(renderer.render(plan(Intent.STATEMENTS_LIST), Language.EN, List.of(result("x", "{\"count\":0}"))))
                .isEqualTo("You have no statements yet.");
        assertThat(renderer.render(plan(Intent.STATEMENTS_LIST), Language.FR, List.of(result("x", """
                {"count":1,"statements":[{"id":1,"courierId":1,"periodStart":"a","periodEnd":"b","status":"RECONCILED","lineCount":13}]}"""))))
                .contains("**1 relevé**").contains("13 lignes");
        assertThat(renderer.render(plan(Intent.ORDERS), Language.EN, List.of(result("x", "{\"totalMatching\":0}"))))
                .startsWith("No orders match");
        assertThat(renderer.render(plan(Intent.ORDERS), Language.EN, List.of(result("x", """
                {"totalMatching":2,"shown":2,"shownTotalAmount":"970.50","orders":[{"trackingNumber":"RPD-1","customerName":"Y",
                 "city":"Casablanca","amountDue":"658.50","status":"DELIVERED"}]}""")))).contains("**2 matching orders**")
                .contains("RPD-1 · Y · Casablanca · 658.50 MAD · DELIVERED");
        assertThat(renderer.render(plan(Intent.OVERVIEW), Language.FR, List.of(result("x", """
                {"expectedCollections":"8903.00","balanceOwed":"4845.50","openDisputes":2,"discrepancies":3}"""))))
                .contains("**Encore dû par les livreurs : 4845,50 MAD**").contains("Litiges ouverts : 2");

        Plan docs = new Plan(Intent.DOCS, null, null, null, null, null, "What does AMOUNT_MISMATCH mean?", "q");
        String snippet = "{\"snippets\":[{\"title\":\"Rules › Classes\",\"text\":\"Classes\\nIntro\\n- AMOUNT_MISMATCH: collected differs\\n\\nMore\\n\\nEven more\"}]}";
        assertThat(renderer.render(docs, Language.EN, List.of(result("searchSettlementDocs", snippet))))
                .isEqualTo("**AMOUNT_MISMATCH** (from the documentation, *Rules › Classes*): collected differs");
        Plan general = new Plan(Intent.DOCS, null, null, null, null, null, "what is a fee", "q");
        assertThat(renderer.render(general, Language.FR, List.of(result("x", snippet))))
                .startsWith("D'après la documentation (*Rules › Classes*, en anglais) :").doesNotContain("Even more");
        assertThat(renderer.render(general, Language.EN, List.of(result("x", "{\"snippets\":[]}")))).contains("won't guess");
    }

    @Test
    void refusalsAndErrorsNeverContainNumbers() throws Exception {
        assertThat(renderer.render(plan(Intent.HELP), Language.EN, List.of())).contains("Which courier owes me the most?");
        assertThat(renderer.render(plan(Intent.HELP), Language.FR, List.of())).contains("Quel livreur");
        assertThat(renderer.render(plan(Intent.FORECAST), Language.EN, List.of())).contains("can't predict");
        assertThat(renderer.render(plan(Intent.FORECAST), Language.FR, List.of())).contains("ne peux pas prédire");
        assertThat(renderer.render(plan(Intent.OUT_OF_SCOPE), Language.FR, List.of())).contains("uniquement");
        assertThat(renderer.render(plan(Intent.OVERVIEW), Language.EN, List.of())).contains("received no data");
        assertThat(renderer.render(plan(Intent.OVERVIEW), Language.FR, List.of())).contains("aucune donnée");

        Plan statement = new Plan(Intent.STATEMENT_EXPLAIN, 12L, null, null, null, null, null, "q");
        assertThat(renderer.render(statement, Language.EN, List.of(error("NOT_FOUND")))).contains("statement #12").contains("won't guess");
        assertThat(renderer.render(statement, Language.FR, List.of(error("NOT_FOUND")))).contains("relevé n°12");
        assertThat(renderer.render(plan(Intent.OVERVIEW), Language.EN, List.of(error("NOT_FOUND")))).contains("won't guess");
        assertThat(renderer.render(plan(Intent.OVERVIEW), Language.FR, List.of(error("NOT_FOUND")))).contains("pas deviner");
        assertThat(renderer.render(plan(Intent.OVERVIEW), Language.EN, List.of(error("UNAVAILABLE")))).contains("isn't reachable");
        assertThat(renderer.render(plan(Intent.OVERVIEW), Language.FR, List.of(error("UNAVAILABLE")))).contains("ne répond pas");
        assertThat(renderer.render(plan(Intent.OVERVIEW), Language.EN, List.of(error("ACCESS_DENIED")))).contains("refused");
        assertThat(renderer.render(plan(Intent.OVERVIEW), Language.FR, List.of(error("ACCESS_DENIED")))).contains("refusé");
        assertThat(renderer.render(plan(Intent.OVERVIEW), Language.EN, List.of(error("BAD_REQUEST")))).contains("rather not guess");
        assertThat(renderer.render(plan(Intent.OVERVIEW), Language.FR, List.of(error("BAD_REQUEST")))).contains("préfère");
    }

    @Test
    void madFormatting() {
        assertThat(AnswerRenderer.mad(mapper.getNodeFactory().textNode("530.50"), true)).isEqualTo("530,50 MAD");
        assertThat(AnswerRenderer.mad(mapper.getNodeFactory().numberNode(new BigDecimal("1.00")), false)).isEqualTo("1.00 MAD");
        assertThat(AnswerRenderer.mad(null, false)).isEqualTo("0.00 MAD");
    }

    private NamedResult error(String code) throws Exception {
        JsonNode json = mapper.readTree("{\"error\":\"" + code + "\",\"message\":\"m\"}");
        return new NamedResult("tool", json);
    }
}
