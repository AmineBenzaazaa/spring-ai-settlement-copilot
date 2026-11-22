package com.msetransport.copilot.demo;

import static org.assertj.core.api.Assertions.assertThat;

import com.msetransport.copilot.chat.Language;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.test.StepVerifier;

class ScriptedChatModelTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final ScriptedChatModel model = new ScriptedChatModel(new DemoPlanner(), new AnswerRenderer(), mapper, Duration.ZERO);
    private static final SystemMessage SYSTEM_EN = new SystemMessage("rules...\n- Answer language: en (reply in English)");
    private static final String COURIERS = "{\"query\":null,\"couriers\":[{\"id\":1,\"name\":\"Speedex Colis\",\"code\":\"speedex\"},"
            + "{\"id\":2,\"name\":\"Rapido Express\",\"code\":\"rapido\"}]}";

    @Test
    void firstTurnAsksForATool() {
        ChatResponse response = model.call(new Prompt(List.of(SYSTEM_EN, new UserMessage("Which courier owes me the most?"))));
        AssistantMessage out = response.getResult().getOutput();
        assertThat(out.getToolCalls()).singleElement().satisfies(c -> {
            assertThat(c.name()).isEqualTo("getCourierBalances");
            assertThat(c.arguments()).isEqualTo("{}");
        });
    }

    @Test
    void namedCourierTakesTwoStepsThenAnswers() {
        UserMessage q = new UserMessage("How much does Speedex owe me?");
        AssistantMessage step1 = model.next(List.of(SYSTEM_EN, q));
        assertThat(step1.getToolCalls().getFirst().name()).isEqualTo("findCourier");

        List<Message> afterFind = List.of(SYSTEM_EN, q, step1, tool("findCourier", COURIERS));
        AssistantMessage step2 = model.next(afterFind);
        assertThat(step2.getToolCalls().getFirst().name()).isEqualTo("getCourierBalance");
        assertThat(step2.getToolCalls().getFirst().arguments()).isEqualTo("{\"courierId\":1}");

        String balance = "{\"courierName\":\"Speedex Colis\",\"deliveredOrders\":8,\"balanceOwed\":\"530.50\"}";
        AssistantMessage answer = model.next(List.of(SYSTEM_EN, q, step1, tool("findCourier", COURIERS), step2,
                tool("getCourierBalance", balance)));
        assertThat(answer.hasToolCalls()).isFalse();
        assertThat(answer.getText()).startsWith("**Speedex Colis** still owes you **530.50 MAD**");
    }

    @Test
    void unknownCourierFallsBackToTheRanking() {
        UserMessage q = new UserMessage("What's my balance with Amana?");
        AssistantMessage step1 = model.next(List.of(q));
        AssistantMessage step2 = model.next(List.of(q, step1, tool("findCourier", COURIERS)));
        assertThat(step2.getToolCalls().getFirst().name()).isEqualTo("getCourierBalances");
    }

    @Test
    void stopsAtTheFirstToolError() {
        UserMessage q = new UserMessage("Why was statement #12 flagged?");
        AssistantMessage step1 = model.next(List.of(q));
        assertThat(step1.getToolCalls().getFirst().arguments()).isEqualTo("{\"statementId\":12}");
        AssistantMessage answer = model.next(List.of(q, step1, tool("getStatementReconciliation", "{\"error\":\"NOT_FOUND\"}")));
        assertThat(answer.getText()).contains("couldn't find statement #12");
        AssistantMessage broken = model.next(List.of(q, step1, tool("getStatementReconciliation", "not json")));
        assertThat(broken.getText()).contains("couldn't get that data");
    }

    @Test
    void buildsToolArgumentsFromTheQuestion() {
        assertThat(firstCall("Show open disputes over 500 MAD on statement #1").arguments())
                .isEqualTo("{\"status\":\"OPEN\",\"minAmount\":500,\"statementId\":1}");
        assertThat(firstCall("Delivered orders in Casablanca over 300 MAD").arguments())
                .isEqualTo("{\"status\":\"DELIVERED\",\"city\":\"Casablanca\",\"minAmount\":300}");
        assertThat(firstCall("What does AMOUNT_MISMATCH mean?").name()).isEqualTo("searchSettlementDocs");
        assertThat(firstCall("Give me an overview").name()).isEqualTo("getSettlementOverview");
        assertThat(firstCall("List my reconciled statements").arguments()).isEqualTo("{\"status\":\"RECONCILED\"}");
    }

    @Test
    void refusalsNeedNoTools() {
        AssistantMessage out = model.next(List.of(new UserMessage("Write me a poem")));
        assertThat(out.hasToolCalls()).isFalse();
        assertThat(out.getText()).contains("only answer questions");
    }

    @Test
    void readsTheAnswerLanguageFromTheSystemPrompt() {
        assertThat(ScriptedChatModel.language(List.of(new SystemMessage("x Answer language: fr")))).isEqualTo(Language.FR);
        assertThat(ScriptedChatModel.language(List.of(new SystemMessage("nothing")))).isEqualTo(Language.EN);
        assertThat(ScriptedChatModel.language(List.of())).isEqualTo(Language.EN);
    }

    @Test
    void streamsTextWordByWordAndToolCallsInOneChunk() {
        Prompt text = new Prompt(List.of(new UserMessage("Write me a poem")));
        StepVerifier.create(model.stream(text).map(r -> r.getResult().getOutput().getText()).reduce(String::concat))
                .assertNext(all -> assertThat(all).startsWith("I only answer"))
                .verifyComplete();
        assertThat(model.stream(text).count().block()).isGreaterThan(10);
        Prompt tools = new Prompt(List.of(new UserMessage("Which courier owes me the most?")));
        StepVerifier.create(model.stream(tools)).assertNext(r -> assertThat(r.getResult().getOutput().hasToolCalls()).isTrue())
                .verifyComplete();
        ScriptedChatModel slow = new ScriptedChatModel(new DemoPlanner(), new AnswerRenderer(), mapper, Duration.ofMillis(1));
        assertThat(slow.stream(text).count().block()).isGreaterThan(10);
        assertThat(model.getDefaultOptions()).isNotNull();
    }

    @Test
    void chunksRebuildTheText() {
        assertThat(String.join("", ScriptedChatModel.chunks("a b\nc  d"))).isEqualTo("a b\nc  d");
        assertThat(ScriptedChatModel.chunks("one two")).containsExactly("one ", "two");
    }

    @Test
    void matchesCourierByNameFirstWordOrCode() throws Exception {
        var couriers = mapper.readTree(COURIERS);
        assertThat(ScriptedChatModel.matchCourier("balance of speedex colis", couriers)).isEqualTo(1L);
        assertThat(ScriptedChatModel.matchCourier("Et Rapido ?", couriers)).isEqualTo(2L);
        assertThat(ScriptedChatModel.matchCourier("rapidos", couriers)).isNull();
        assertThat(ScriptedChatModel.matchCourier(null, couriers)).isNull();
    }

    private AssistantMessage.ToolCall firstCall(String question) {
        return model.next(List.of(new UserMessage(question))).getToolCalls().getFirst();
    }

    private static ToolResponseMessage tool(String name, String json) {
        return ToolResponseMessage.builder()
                .responses(List.of(new ToolResponseMessage.ToolResponse("id-" + name, name, json))).build();
    }
}
