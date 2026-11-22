package com.benzaazaa.copilot.tools;

import com.benzaazaa.copilot.security.Caller;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.springframework.ai.chat.model.ToolContext;

/**
 * Everything one question collects while the model works: the caller (and their token), each
 * tool call's trace, the typed tool outputs (for charts and grounding) and the source rows.
 *
 * <p>Handed to the tools through Spring AI's {@link ToolContext}, which the model never sees.
 * Tool calls of one turn run sequentially, so a plain list is enough (no shared state between turns).
 */
public final class TurnContext {

    public static final String KEY = "copilot.turn";

    private final Caller caller;
    private final Consumer<ToolCallTrace> onToolCall;
    private final List<ToolCallTrace> traces = new ArrayList<>();
    private final List<Object> outputs = new ArrayList<>();
    private final Map<String, SourceRef> sources = new LinkedHashMap<>();
    private final List<String> payloads = new ArrayList<>();

    private int pendingRows;
    private int pendingFlagged;
    private String pendingStatus;

    public TurnContext(Caller caller, Consumer<ToolCallTrace> onToolCall) {
        this.caller = caller;
        this.onToolCall = onToolCall;
    }

    public static TurnContext from(ToolContext toolContext) {
        Object turn = toolContext == null ? null : toolContext.getContext().get(KEY);
        if (!(turn instanceof TurnContext ctx)) {
            throw new IllegalStateException("Tool called without a TurnContext");
        }
        return ctx;
    }

    public Caller caller() {
        return caller;
    }

    public String token() {
        return caller.token();
    }

    /** Called by a tool when it has its data. */
    public void recordResult(Object output, int rows, int flagged, List<SourceRef> rowSources) {
        outputs.add(output);
        pendingRows = rows;
        pendingFlagged = flagged;
        pendingStatus = "OK";
        rowSources.forEach(s -> sources.putIfAbsent(s.kind() + ":" + s.id(), s));
    }

    /** Called by a tool when the platform said no (404, 401/403, down). */
    public void recordFailure(String status) {
        pendingRows = 0;
        pendingFlagged = 0;
        pendingStatus = status;
    }

    /** Called by {@link TracingToolCallback} around every tool call. */
    ToolCallTrace complete(String tool, String arguments, String payload, long durationMs, boolean threw) {
        String status = threw ? "ERROR" : pendingStatus == null ? "OK" : pendingStatus;
        ToolCallTrace trace = new ToolCallTrace(traces.size() + 1, tool, arguments, status, pendingRows, durationMs,
                pendingFlagged);
        traces.add(trace);
        if (payload != null) {
            payloads.add(payload);
        }
        pendingRows = 0;
        pendingFlagged = 0;
        pendingStatus = null;
        onToolCall.accept(trace);
        return trace;
    }

    public List<ToolCallTrace> traces() {
        return Collections.unmodifiableList(traces);
    }

    public List<Object> outputs() {
        return Collections.unmodifiableList(outputs);
    }

    public List<SourceRef> sources() {
        return List.copyOf(sources.values());
    }

    /** Raw JSON the model received from each tool: the ground truth for the grounding check. */
    public List<String> payloads() {
        return Collections.unmodifiableList(payloads);
    }
}
