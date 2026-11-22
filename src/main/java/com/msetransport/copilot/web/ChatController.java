package com.benzaazaa.copilot.web;

import com.benzaazaa.copilot.chat.ChatAnswer;
import com.benzaazaa.copilot.chat.ChatRequest;
import com.benzaazaa.copilot.chat.CopilotEvent;
import com.benzaazaa.copilot.chat.CopilotService;
import com.benzaazaa.copilot.security.Caller;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/copilot/chat")
@Tag(name = "Chat", description = "Ask the copilot; streaming (SSE) or a single JSON answer")
public class ChatController {

    private final CopilotService copilotService;

    public ChatController(CopilotService copilotService) {
        this.copilotService = copilotService;
    }

    /**
     * Server-Sent Events: {@code meta}, {@code tool}*, {@code token}*, {@code chart}?, {@code sources},
     * {@code verification}, {@code done} (or {@code error}). POST, not GET, because the question is a
     * body and the bearer token is a header: the browser reads it with {@code fetch}, not EventSource.
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Ask a question, stream the answer as Server-Sent Events")
    public Flux<ServerSentEvent<CopilotEvent>> stream(JwtAuthenticationToken authentication,
                                                      @Valid @RequestBody ChatRequest request) {
        return copilotService.stream(Caller.from(authentication), request)
                .map(event -> ServerSentEvent.<CopilotEvent>builder(event).event(event.type()).build());
    }

    @PostMapping
    @Operation(summary = "Ask a question, get the complete answer as JSON")
    public ChatAnswer ask(JwtAuthenticationToken authentication, @Valid @RequestBody ChatRequest request) {
        return copilotService.answer(Caller.from(authentication), request);
    }
}
