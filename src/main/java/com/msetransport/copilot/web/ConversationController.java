package com.msetransport.copilot.web;

import com.msetransport.copilot.chat.CopilotService;
import com.msetransport.copilot.security.Caller;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Conversation memory of the signed-in user (other users' conversations are simply empty). */
@RestController
@Validated
@RequestMapping("/api/copilot/conversations")
@Tag(name = "Conversations")
public class ConversationController {

    private final ChatMemory chatMemory;

    public ConversationController(ChatMemory chatMemory) {
        this.chatMemory = chatMemory;
    }

    @GetMapping("/{id}")
    public List<MessageView> history(JwtAuthenticationToken authentication,
                                     @PathVariable @Pattern(regexp = "[A-Za-z0-9-]{1,64}") String id) {
        return chatMemory.get(CopilotService.memoryKey(Caller.from(authentication), id)).stream()
                .map(m -> new MessageView(m.getMessageType().name().toLowerCase(), m.getText()))
                .toList();
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clear(JwtAuthenticationToken authentication, @PathVariable @Pattern(regexp = "[A-Za-z0-9-]{1,64}") String id) {
        chatMemory.clear(CopilotService.memoryKey(Caller.from(authentication), id));
    }

    public record MessageView(String role, String text) {
    }
}
