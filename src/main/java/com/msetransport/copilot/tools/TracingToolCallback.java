package com.msetransport.copilot.tools;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

/**
 * Decorator around every Spring AI {@link ToolCallback}: measures the call, and reports it to the
 * {@link TurnContext} so the UI can show the trace live while the model is still working.
 */
public final class TracingToolCallback implements ToolCallback {

    private final ToolCallback delegate;

    public TracingToolCallback(ToolCallback delegate) {
        this.delegate = delegate;
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return delegate.getToolDefinition();
    }

    @Override
    public ToolMetadata getToolMetadata() {
        return delegate.getToolMetadata();
    }

    @Override
    public String call(String toolInput) {
        throw new IllegalStateException("Copilot tools need a ToolContext");
    }

    @Override
    public String call(String toolInput, ToolContext toolContext) {
        TurnContext turn = TurnContext.from(toolContext);
        long start = System.nanoTime();
        String result = null;
        boolean threw = true;
        try {
            result = delegate.call(toolInput, toolContext);
            threw = false;
            return result;
        } finally {
            long ms = (System.nanoTime() - start) / 1_000_000;
            turn.complete(getToolDefinition().name(), toolInput, result, ms, threw);
        }
    }
}
