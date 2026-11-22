package com.msetransport.copilot.demo;

import com.msetransport.copilot.chat.Language;
import com.msetransport.copilot.demo.AnswerRenderer.NamedResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import reactor.core.publisher.Flux;

/**
 * A real Spring AI {@link ChatModel}, but scripted: it reads the prompt like an LLM would (system
 * prompt, history, question, tool results) and answers with tool calls or text, deterministically.
 *
 * <ul>
 *   <li>Last message is the user's question: ask for the first tool call of the plan (or answer
 *       directly for help, forecast and out-of-scope questions).</li>
 *   <li>Last message is a tool result: either request the next tool (finding a courier by name,
 *       then its balance) or render the final answer from the JSON.</li>
 * </ul>
 *
 * It goes through exactly the same agent loop, tool execution, guards and SSE pipeline as Claude.
 */
public class ScriptedChatModel implements ChatModel {

    private static final Pattern LANGUAGE = Pattern.compile("Answer language: (en|fr)");

    private final DemoPlanner planner;
    private final AnswerRenderer renderer;
    private final ObjectMapper objectMapper;
    private final Duration tokenDelay;

    public ScriptedChatModel(DemoPlanner planner, AnswerRenderer renderer, ObjectMapper objectMapper, Duration tokenDelay) {
        this.planner = planner;
        this.renderer = renderer;
        this.objectMapper = objectMapper;
        this.tokenDelay = tokenDelay;
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        return new ChatResponse(List.of(new Generation(next(prompt.getInstructions()))));
    }

    /** Tool calls arrive as one chunk; text arrives word by word, like a streaming LLM. */
    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        AssistantMessage message = next(prompt.getInstructions());
        if (message.hasToolCalls()) {
            return Flux.just(new ChatResponse(List.of(new Generation(message))));
        }
        Flux<ChatResponse> words = Flux.fromIterable(chunks(message.getText()))
                .map(word -> new ChatResponse(List.of(new Generation(new AssistantMessage(word)))));
        return tokenDelay.isZero() ? words : words.delayElements(tokenDelay);
    }

    @Override
    public ChatOptions getDefaultOptions() {
        return ToolCallingChatOptions.builder().build();
    }

    AssistantMessage next(List<Message> messages) {
        Language language = language(messages);
        int lastUser = lastIndexOf(messages, MessageType.USER);
        String question = lastUser < 0 ? "" : messages.get(lastUser).getText();
        String previous = previousUserQuestion(messages, lastUser);
        Plan plan = planner.plan(question, previous);

        List<NamedResult> results = new ArrayList<>();
        for (int i = lastUser + 1; i < messages.size(); i++) {
            if (messages.get(i) instanceof ToolResponseMessage tool) {
                tool.getResponses().forEach(r -> results.add(new NamedResult(r.name(), parse(r.responseData()))));
            }
        }
        Optional<AssistantMessage.ToolCall> call = nextCall(plan, results);
        if (call.isPresent()) {
            return AssistantMessage.builder().content("").toolCalls(List.of(call.get())).build();
        }
        return new AssistantMessage(renderer.render(plan, language, results));
    }

    /** The multi-step part of the "plan": what to call given what has been returned so far. */
    Optional<AssistantMessage.ToolCall> nextCall(Plan plan, List<NamedResult> results) {
        if (!plan.needsTools()) {
            return Optional.empty();
        }
        int step = results.size();
        if (step > 0 && results.getLast().json().has("error")) {
            return Optional.empty();
        }
        ObjectNode args = objectMapper.createObjectNode();
        return switch (plan.intent()) {
            case COURIER_BALANCE -> {
                if (step == 0) {
                    args.put("query", "");
                    yield Optional.of(call(step, "findCourier", args));
                }
                if (step == 1) {
                    Long courierId = matchCourier(plan.subject(), results.getFirst().json());
                    if (courierId == null) {
                        yield Optional.of(call(step, "getCourierBalances", args));
                    }
                    args.put("courierId", courierId);
                    yield Optional.of(call(step, "getCourierBalance", args));
                }
                yield Optional.empty();
            }
            default -> step > 0 ? Optional.empty() : Optional.of(firstCall(plan, args));
        };
    }

    private AssistantMessage.ToolCall firstCall(Plan plan, ObjectNode args) {
        return switch (plan.intent()) {
            case TOP_COURIER -> call(0, "getCourierBalances", args);
            case OVERVIEW -> call(0, "getSettlementOverview", args);
            case STATEMENT_EXPLAIN -> call(0, "getStatementReconciliation", args.put("statementId", plan.statementId()));
            case STATEMENTS_LIST -> call(0, "listStatements", putIfPresent(args, "status", plan.status()));
            case DISPUTES -> {
                putIfPresent(args, "status", plan.status());
                putIfPresent(args, "minAmount", plan.minAmount());
                if (plan.statementId() != null) {
                    args.put("statementId", plan.statementId());
                }
                yield call(0, "listDisputes", args);
            }
            case ORDERS -> {
                putIfPresent(args, "status", plan.status());
                putIfPresent(args, "city", plan.city());
                putIfPresent(args, "minAmount", plan.minAmount());
                putIfPresent(args, "maxAmount", plan.maxAmount());
                putIfPresent(args, "text", plan.text());
                yield call(0, "searchOrders", args);
            }
            case DOCS -> call(0, "searchSettlementDocs", args.put("query", plan.text()));
            default -> throw new IllegalStateException("No tool for " + plan.intent());
        };
    }

    /** Finds a courier whose name, first word or code appears in the question. */
    static Long matchCourier(String question, JsonNode matches) {
        String q = com.msetransport.copilot.knowledge.TextNormalizer.fold(question == null ? "" : question);
        for (JsonNode c : matches.path("couriers")) {
            String name = com.msetransport.copilot.knowledge.TextNormalizer.fold(c.path("name").asText());
            String firstWord = name.split("\\s+")[0];
            String code = c.path("code").asText().toLowerCase();
            if ((!name.isBlank() && q.contains(name)) || (firstWord.length() >= 3 && q.matches("(?s).*\\b" + Pattern.quote(firstWord) + "\\b.*"))
                    || (code.length() >= 3 && q.matches("(?s).*\\b" + Pattern.quote(code) + "\\b.*"))) {
                return c.path("id").asLong();
            }
        }
        return null;
    }

    private AssistantMessage.ToolCall call(int step, String tool, ObjectNode args) {
        return new AssistantMessage.ToolCall("demo-" + step + "-" + tool, "function", tool, args.toString());
    }

    private static ObjectNode putIfPresent(ObjectNode node, String field, Object value) {
        if (value instanceof BigDecimal d) {
            node.put(field, d);
        } else if (value != null) {
            node.put(field, value.toString());
        }
        return node;
    }

    private JsonNode parse(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException ex) {
            return objectMapper.createObjectNode().put("error", "UNREADABLE");
        }
    }

    static Language language(List<Message> messages) {
        for (Message m : messages) {
            if (m instanceof SystemMessage) {
                Matcher matcher = LANGUAGE.matcher(m.getText());
                if (matcher.find()) {
                    return "fr".equals(matcher.group(1)) ? Language.FR : Language.EN;
                }
            }
        }
        return Language.EN;
    }

    private static int lastIndexOf(List<Message> messages, MessageType type) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i).getMessageType() == type) {
                return i;
            }
        }
        return -1;
    }

    private static String previousUserQuestion(List<Message> messages, int lastUser) {
        for (int i = lastUser - 1; i >= 0; i--) {
            if (messages.get(i).getMessageType() == MessageType.USER) {
                return messages.get(i).getText();
            }
        }
        return null;
    }

    /** Split after each space, keeping the spaces, so concatenating the chunks gives the text back. */
    static List<String> chunks(String text) {
        List<String> chunks = new ArrayList<>();
        int start = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == ' ' || text.charAt(i) == '\n') {
                chunks.add(text.substring(start, i + 1));
                start = i + 1;
            }
        }
        if (start < text.length()) {
            chunks.add(text.substring(start));
        }
        return chunks;
    }
}
