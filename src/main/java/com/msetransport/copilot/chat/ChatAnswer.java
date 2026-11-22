package com.msetransport.copilot.chat;

import com.msetransport.copilot.guard.GroundingGuard.Verification;
import com.msetransport.copilot.tools.SourceRef;
import com.msetransport.copilot.tools.ToolCallTrace;
import java.util.List;

/** Non-streaming response: the same information as the SSE events, collected into one object. */
public record ChatAnswer(String conversationId, String mode, String language, String answer,
                         List<ToolCallTrace> toolCalls, List<SourceRef> sources, ChartSpec chart,
                         Verification verification, String error, long durationMs) {
}
