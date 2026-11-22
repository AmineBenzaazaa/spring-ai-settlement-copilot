package com.msetransport.copilot.config;

import java.util.List;
import org.springframework.ai.tool.ToolCallback;

/** The copilot's tools, each wrapped for tracing. A holder type so Spring AI does not auto-register them globally. */
public record CopilotToolbox(List<ToolCallback> callbacks) {

    public List<String> names() {
        return callbacks.stream().map(c -> c.getToolDefinition().name()).toList();
    }
}
