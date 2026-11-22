package com.benzaazaa.copilot.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.benzaazaa.copilot.security.Caller;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

class TracingToolCallbackTest {

    private final List<ToolCallTrace> traces = new ArrayList<>();
    private final TurnContext turn = new TurnContext(new Caller("atlas", "MERCHANT", 1L, "t"), traces::add);
    private final ToolContext ctx = new ToolContext(Map.of(TurnContext.KEY, turn));

    private ToolCallback delegate(String name) {
        ToolCallback delegate = mock(ToolCallback.class);
        when(delegate.getToolDefinition()).thenReturn(ToolDefinition.builder().name(name).description("d").inputSchema("{}").build());
        when(delegate.getToolMetadata()).thenReturn(ToolMetadata.builder().build());
        return delegate;
    }

    @Test
    void recordsEachCallWithItsPayload() {
        ToolCallback delegate = delegate("getCourierBalances");
        when(delegate.call(any(), any())).thenAnswer(inv -> {
            turn.recordResult("out", 2, 0, List.of(new SourceRef("courier", "1", "Speedex", null)));
            return "{\"x\":\"1.00\"}";
        });
        TracingToolCallback tracing = new TracingToolCallback(delegate);
        assertThat(tracing.getToolMetadata()).isNotNull();
        assertThat(tracing.call("{}", ctx)).isEqualTo("{\"x\":\"1.00\"}");
        assertThat(traces).singleElement().satisfies(t -> {
            assertThat(t.tool()).isEqualTo("getCourierBalances");
            assertThat(t.status()).isEqualTo("OK");
            assertThat(t.rows()).isEqualTo(2);
            assertThat(t.index()).isEqualTo(1);
        });
        assertThat(turn.payloads()).containsExactly("{\"x\":\"1.00\"}");
        assertThat(turn.outputs()).containsExactly("out");
        assertThat(turn.traces()).hasSize(1);
        assertThat(turn.token()).isEqualTo("t");
        assertThat(turn.caller().username()).isEqualTo("atlas");
    }

    @Test
    void aThrowingToolIsTracedAsError() {
        ToolCallback delegate = delegate("boom");
        when(delegate.call(any(), any())).thenThrow(new IllegalStateException("x"));
        assertThatThrownBy(() -> new TracingToolCallback(delegate).call("{}", ctx)).isInstanceOf(IllegalStateException.class);
        assertThat(traces).singleElement().satisfies(t -> assertThat(t.status()).isEqualTo("ERROR"));
    }

    @Test
    void refusesCallsWithoutContext() {
        TracingToolCallback tracing = new TracingToolCallback(delegate("x"));
        assertThatThrownBy(() -> tracing.call("{}")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> tracing.call("{}", new ToolContext(Map.of()))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> TurnContext.from(null)).isInstanceOf(IllegalStateException.class);
    }
}
