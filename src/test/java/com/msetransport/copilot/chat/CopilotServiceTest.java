package com.msetransport.copilot.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.msetransport.copilot.config.AiConfigTestAccess;
import com.msetransport.copilot.config.CopilotProperties;
import com.msetransport.copilot.config.CopilotToolbox;
import com.msetransport.copilot.demo.AnswerRenderer;
import com.msetransport.copilot.demo.DemoPlanner;
import com.msetransport.copilot.demo.ScriptedChatModel;
import com.msetransport.copilot.guard.GroundingGuard;
import com.msetransport.copilot.guard.InjectionGuard;
import com.msetransport.copilot.knowledge.KnowledgeTools;
import com.msetransport.copilot.security.Caller;
import com.msetransport.copilot.settlement.DeskLinks;
import com.msetransport.copilot.settlement.SettlementApiClient;
import com.msetransport.copilot.settlement.SettlementModels;
import com.msetransport.copilot.tools.SettlementTools;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.vectorstore.VectorStore;
import reactor.core.publisher.Flux;

/** The agent loop with the real tools, tool manager, memory and guards; only the platform is mocked. */
class CopilotServiceTest {

    private final Caller atlas = new Caller("atlas", "MERCHANT", 1L, "tok");
    private SettlementApiClient client;
    private ChatMemory memory;
    private CopilotToolbox toolbox;
    private CopilotProperties props;

    @BeforeEach
    void setUp() {
        client = mock(SettlementApiClient.class);
        props = new CopilotProperties(CopilotProperties.CopilotMode.DEMO, "claude-x",
                new CopilotProperties.Settlement("http://api", Duration.ofSeconds(1), Duration.ofSeconds(1)),
                "http://desk", 20, 3, 2000, new CopilotProperties.Demo(Duration.ZERO));
        SettlementTools tools = new SettlementTools(client, new DeskLinks(props), new InjectionGuard());
        toolbox = AiConfigTestAccess.toolbox(tools, new KnowledgeTools(mock(VectorStore.class)));
        memory = MessageWindowChatMemory.builder().chatMemoryRepository(new InMemoryChatMemoryRepository()).build();
        when(client.overview("tok")).thenReturn(new SettlementModels.Overview(1L, "MAD", d("10.00"), d("5.00"), d("1.00"),
                d("4.00"), d("5.00"), 0, d("0"), 0, d("0"), List.of(
                new SettlementModels.CourierPosition(2L, "Rapido Express", "rapido", true, 1, d("10.00"), d("5.00"),
                        d("1.00"), d("4.00"), d("5.00")))));
    }

    private CopilotService service(ChatModel model) {
        return new CopilotService(model, ToolCallingManager.builder().build(), toolbox, memory, new PromptBuilder(),
                new ChartSpecFactory(), new GroundingGuard(), props, Clock.fixed(Instant.parse("2026-11-22T10:00:00Z"), ZoneOffset.UTC));
    }

    private static BigDecimal d(String v) {
        return new BigDecimal(v);
    }

    private ScriptedChatModel scripted() {
        return new ScriptedChatModel(new DemoPlanner(), new AnswerRenderer(), new ObjectMapper(), Duration.ZERO);
    }

    @Test
    void streamsMetaToolTokensChartSourcesVerificationDoneInOrder() {
        List<CopilotEvent> events = service(scripted()).stream(atlas,
                new ChatRequest("conv-1", "Which courier owes me the most?", "en")).collectList().block();
        List<String> types = events.stream().map(CopilotEvent::type).distinct().toList();
        assertThat(types).containsExactly("meta", "tool", "token", "chart", "sources", "verification", "done");
        CopilotEvent.Meta meta = (CopilotEvent.Meta) events.getFirst();
        assertThat(meta.conversationId()).isEqualTo("conv-1");
        assertThat(meta.model()).isEqualTo("scripted-offline");
        CopilotEvent.Done done = (CopilotEvent.Done) events.getLast();
        assertThat(done.answer()).startsWith("**Rapido Express** owes you the most: **5.00 MAD**");
        CopilotEvent.Grounding grounding = events.stream().filter(CopilotEvent.Grounding.class::isInstance)
                .map(CopilotEvent.Grounding.class::cast).findFirst().orElseThrow();
        assertThat(grounding.verification().grounded()).isTrue();
    }

    @Test
    void savesTheTurnInMemoryKeyedByUserAndConversation() {
        CopilotService service = service(scripted());
        ChatAnswer answer = service.answer(atlas, new ChatRequest(null, "Give me an overview", null));
        assertThat(answer.conversationId()).isNotBlank();
        assertThat(answer.toolCalls()).extracting("tool").containsExactly("getSettlementOverview");
        assertThat(answer.sources()).hasSize(1);
        assertThat(answer.chart()).isNotNull();
        assertThat(answer.error()).isNull();
        assertThat(memory.get(CopilotService.memoryKey(atlas, answer.conversationId()))).hasSize(2);
        assertThat(memory.get(CopilotService.memoryKey(new Caller("medina", "MERCHANT", 2L, "x"), answer.conversationId()))).isEmpty();
    }

    @Test
    void frenchQuestionGetsAFrenchAnswer() {
        ChatAnswer answer = service(scripted()).answer(atlas, new ChatRequest(null, "Quel livreur me doit le plus ?", "auto"));
        assertThat(answer.language()).isEqualTo("fr");
        assertThat(answer.answer()).contains("vous doit le plus : **5,00 MAD**");
        assertThat(answer.verification().grounded()).isTrue();
    }

    @Test
    void stopsAModelThatNeverStopsCallingTools() {
        ChatModel looping = new ChatModel() {
            @Override
            public ChatResponse call(Prompt prompt) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Flux<ChatResponse> stream(Prompt prompt) {
                AssistantMessage msg = AssistantMessage.builder().content("Checking.")
                        .toolCalls(List.of(new AssistantMessage.ToolCall("id" + System.nanoTime(), "function",
                                "getSettlementOverview", "{}"))).build();
                return Flux.just(new ChatResponse(List.of(new Generation(msg))));
            }
        };
        ChatAnswer answer = service(looping).answer(atlas, new ChatRequest(null, "loop", "en"));
        assertThat(answer.toolCalls()).hasSize(3);
        assertThat(answer.answer()).endsWith("Could you make it more specific?");
        ChatAnswer fr = service(looping).answer(atlas, new ChatRequest(null, "boucle", "fr"));
        assertThat(fr.answer()).endsWith("Pouvez-vous la préciser ?");
    }

    @Test
    void hidesThinkingBlocksAndFlagsUngroundedNumbers() {
        ChatModel thinker = new ChatModel() {
            @Override
            public ChatResponse call(Prompt prompt) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Flux<ChatResponse> stream(Prompt prompt) {
                AssistantMessage thinking = AssistantMessage.builder().content("secret reasoning")
                        .properties(Map.of("signature", "sig")).build();
                return Flux.just(new ChatResponse(List.of(new Generation(thinking))),
                        new ChatResponse(List.of(new Generation(new AssistantMessage("You are owed 999.99 MAD.")))));
            }
        };
        ChatAnswer answer = service(thinker).answer(atlas, new ChatRequest(null, "how much?", "en"));
        assertThat(answer.answer()).isEqualTo("You are owed 999.99 MAD.");
        assertThat(answer.verification().grounded()).isFalse();
        assertThat(answer.verification().ungrounded()).containsExactly("999.99");
    }

    @Test
    void modelFailureBecomesAnErrorEvent() {
        ChatModel broken = new ChatModel() {
            @Override
            public ChatResponse call(Prompt prompt) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Flux<ChatResponse> stream(Prompt prompt) {
                return Flux.error(new IllegalStateException("429 from provider"));
            }
        };
        ChatAnswer answer = service(broken).answer(atlas, new ChatRequest(null, "hi", "en"));
        assertThat(answer.error()).startsWith("MODEL_ERROR");
        assertThat(answer.answer()).isEmpty();
    }

    @Test
    void cancellingTheStreamIsSafe() {
        List<CopilotEvent> first = service(scripted()).stream(atlas, new ChatRequest(null, "Give me an overview", "en"))
                .take(2).collectList().block();
        assertThat(first).hasSize(2);
    }
}
