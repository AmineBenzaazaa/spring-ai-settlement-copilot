package com.benzaazaa.copilot.chat;

import com.benzaazaa.copilot.config.CopilotProperties;
import com.benzaazaa.copilot.config.CopilotToolbox;
import com.benzaazaa.copilot.guard.GroundingGuard;
import com.benzaazaa.copilot.guard.GroundingGuard.Verification;
import com.benzaazaa.copilot.security.Caller;
import com.benzaazaa.copilot.tools.TurnContext;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

/**
 * The agent loop, written out explicitly instead of hidden inside the model:
 *
 * <pre>
 *   system prompt + memory + question
 *        │
 *        ▼
 *   ┌─ stream the model ──► tokens go straight to the browser
 *   │      │ tool calls?
 *   │      ├─ no  ──► answer done
 *   │      └─ yes ──► ToolCallingManager runs them (trace events), results appended
 *   └──────┘ (at most maxToolRounds)
 *        │
 *        ▼
 *   chart (from tool data) · sources · grounding check · save to memory
 * </pre>
 *
 * Same loop for Claude and for the offline demo model: only the {@link ChatModel} bean changes.
 * Spring AI's internal tool execution is switched off so this class sees (and streams) each step.
 */
@Service
public class CopilotService {

    private static final Logger log = LoggerFactory.getLogger(CopilotService.class);

    private final ChatModel chatModel;
    private final ToolCallingManager toolCallingManager;
    private final CopilotToolbox toolbox;
    private final ChatMemory chatMemory;
    private final PromptBuilder promptBuilder;
    private final ChartSpecFactory chartSpecFactory;
    private final GroundingGuard groundingGuard;
    private final CopilotProperties properties;
    private final Clock clock;

    public CopilotService(ChatModel chatModel, ToolCallingManager toolCallingManager, CopilotToolbox toolbox,
                          ChatMemory chatMemory, PromptBuilder promptBuilder, ChartSpecFactory chartSpecFactory,
                          GroundingGuard groundingGuard, CopilotProperties properties, Clock clock) {
        this.chatModel = chatModel;
        this.toolCallingManager = toolCallingManager;
        this.toolbox = toolbox;
        this.chatMemory = chatMemory;
        this.promptBuilder = promptBuilder;
        this.chartSpecFactory = chartSpecFactory;
        this.groundingGuard = groundingGuard;
        this.properties = properties;
        this.clock = clock;
    }

    /** Streams the events of one question. Runs on a bounded-elastic thread; cancelling stops the loop. */
    public Flux<CopilotEvent> stream(Caller caller, ChatRequest request) {
        return Flux.<CopilotEvent>create(sink -> {
            try {
                run(caller, request, sink::next, sink::isCancelled);
            } catch (RuntimeException ex) {
                log.warn("Copilot turn failed for {}: {}", caller, ex.toString());
                sink.next(new CopilotEvent.Error("MODEL_ERROR",
                        "The assistant could not finish this answer. Please try again."));
            }
            sink.complete();
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /** Same turn, collected into one response object (handy for curl, tests and the eval suite). */
    public ChatAnswer answer(Caller caller, ChatRequest request) {
        List<CopilotEvent> events = stream(caller, request).collectList().block();
        return collect(events == null ? List.of() : events);
    }

    static ChatAnswer collect(List<CopilotEvent> events) {
        String conversationId = null;
        String mode = null;
        String language = null;
        StringBuilder text = new StringBuilder();
        List<com.benzaazaa.copilot.tools.ToolCallTrace> traces = new ArrayList<>();
        List<com.benzaazaa.copilot.tools.SourceRef> sources = List.of();
        ChartSpec chart = null;
        Verification verification = null;
        String error = null;
        long duration = 0;
        for (CopilotEvent event : events) {
            switch (event) {
                case CopilotEvent.Meta m -> {
                    conversationId = m.conversationId();
                    mode = m.mode();
                    language = m.language();
                }
                case CopilotEvent.ToolCall t -> traces.add(t.trace());
                case CopilotEvent.Token t -> text.append(t.text());
                case CopilotEvent.Chart c -> chart = c.chart();
                case CopilotEvent.Sources s -> sources = s.sources();
                case CopilotEvent.Grounding g -> verification = g.verification();
                case CopilotEvent.Done d -> duration = d.durationMs();
                case CopilotEvent.Error e -> error = e.code() + ": " + e.message();
            }
        }
        return new ChatAnswer(conversationId, mode, language, text.toString(), traces, sources, chart, verification,
                error, duration);
    }

    private void run(Caller caller, ChatRequest request, Consumer<CopilotEvent> emit, BooleanSupplier cancelled) {
        long start = System.nanoTime();
        String conversationId = request.conversationId() == null || request.conversationId().isBlank()
                ? UUID.randomUUID().toString() : request.conversationId();
        String question = request.message().strip();
        Language language = Language.resolve(request.language(), question);
        emit.accept(new CopilotEvent.Meta(conversationId, properties.mode().label(), properties.activeModel(), language.code()));

        String memoryKey = memoryKey(caller, conversationId);
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(promptBuilder.systemPrompt(caller, language, LocalDate.now(clock))));
        messages.addAll(chatMemory.get(memoryKey));
        messages.add(new UserMessage(question));

        TurnContext turn = new TurnContext(caller, trace -> emit.accept(new CopilotEvent.ToolCall(trace)));
        ToolCallingChatOptions options = ToolCallingChatOptions.builder()
                .toolCallbacks(toolbox.callbacks())
                .toolContext(Map.of(TurnContext.KEY, turn))
                .internalToolExecutionEnabled(false)
                .build();

        StringBuilder answer = new StringBuilder();
        boolean finished = false;
        for (int round = 0; round < properties.maxToolRounds() && !cancelled.getAsBoolean(); round++) {
            Prompt prompt = new Prompt(messages, options);
            StringBuilder roundText = new StringBuilder();
            Map<String, AssistantMessage.ToolCall> toolCalls = new LinkedHashMap<>();
            for (ChatResponse chunk : chatModel.stream(prompt).toIterable()) {
                if (cancelled.getAsBoolean()) {
                    return;
                }
                for (Generation generation : chunk.getResults()) {
                    AssistantMessage out = generation.getOutput();
                    if (isThinking(out)) {
                        continue;
                    }
                    String text = out.getText();
                    if (text != null && !text.isEmpty()) {
                        roundText.append(text);
                        emit.accept(new CopilotEvent.Token(text));
                    }
                    out.getToolCalls().forEach(call -> toolCalls.put(call.id(), call));
                }
            }
            answer.append(roundText);
            if (toolCalls.isEmpty()) {
                finished = true;
                break;
            }
            AssistantMessage assistant = AssistantMessage.builder()
                    .content(roundText.toString())
                    .toolCalls(List.copyOf(toolCalls.values()))
                    .build();
            ToolExecutionResult result = toolCallingManager.executeToolCalls(prompt,
                    new ChatResponse(List.of(new Generation(assistant))));
            messages = new ArrayList<>(result.conversationHistory());
            if (!answer.isEmpty() && !Character.isWhitespace(answer.charAt(answer.length() - 1))) {
                answer.append("\n\n");
                emit.accept(new CopilotEvent.Token("\n\n"));
            }
        }
        if (cancelled.getAsBoolean()) {
            return;
        }
        if (!finished) {
            String stop = language == Language.FR
                    ? "Je me suis arrêté : trop d'étapes pour cette question. Pouvez-vous la préciser ?"
                    : "I stopped here: this question needed too many steps. Could you make it more specific?";
            answer.append(stop);
            emit.accept(new CopilotEvent.Token(stop));
        }

        String finalAnswer = answer.toString().strip();
        chartSpecFactory.from(turn.outputs(), language).ifPresent(chart -> emit.accept(new CopilotEvent.Chart(chart)));
        emit.accept(new CopilotEvent.Sources(turn.sources()));
        emit.accept(new CopilotEvent.Grounding(groundingGuard.check(finalAnswer, turn.payloads())));
        chatMemory.add(memoryKey, List.of(new UserMessage(question), new AssistantMessage(finalAnswer)));
        emit.accept(new CopilotEvent.Done(conversationId, finalAnswer, (System.nanoTime() - start) / 1_000_000));
    }

    /** Memory is keyed by user + conversation: guessing someone else's conversation id gets you nothing. */
    public static String memoryKey(Caller caller, String conversationId) {
        return caller.username() + ":" + conversationId;
    }

    /** Claude's thinking blocks arrive as assistant generations carrying a signature: never shown or saved. */
    private static boolean isThinking(AssistantMessage message) {
        Map<String, Object> props = message.getMetadata();
        return props != null && (props.containsKey("signature") || props.containsKey("data")) && !message.hasToolCalls();
    }
}
