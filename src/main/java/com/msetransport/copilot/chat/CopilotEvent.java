package com.msetransport.copilot.chat;

import com.msetransport.copilot.guard.GroundingGuard.Verification;
import com.msetransport.copilot.tools.SourceRef;
import com.msetransport.copilot.tools.ToolCallTrace;
import java.util.List;

/**
 * Everything the copilot streams to the browser for one question, in order:
 * {@code meta, tool*, token*, chart?, sources, verification, done} (or {@code error}).
 * The SSE event name is {@link #type()}.
 */
public sealed interface CopilotEvent {

    String type();

    record Meta(String conversationId, String mode, String model, String language) implements CopilotEvent {
        public String type() {
            return "meta";
        }
    }

    record ToolCall(ToolCallTrace trace) implements CopilotEvent {
        public String type() {
            return "tool";
        }
    }

    record Token(String text) implements CopilotEvent {
        public String type() {
            return "token";
        }
    }

    record Chart(ChartSpec chart) implements CopilotEvent {
        public String type() {
            return "chart";
        }
    }

    record Sources(List<SourceRef> sources) implements CopilotEvent {
        public String type() {
            return "sources";
        }
    }

    record Grounding(Verification verification) implements CopilotEvent {
        public String type() {
            return "verification";
        }
    }

    record Done(String conversationId, String answer, long durationMs) implements CopilotEvent {
        public String type() {
            return "done";
        }
    }

    record Error(String code, String message) implements CopilotEvent {
        public String type() {
            return "error";
        }
    }
}
