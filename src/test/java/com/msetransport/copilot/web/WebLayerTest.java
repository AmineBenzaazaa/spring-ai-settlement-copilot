package com.msetransport.copilot.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.msetransport.copilot.chat.ChatAnswer;
import com.msetransport.copilot.chat.ChatRequest;
import com.msetransport.copilot.chat.CopilotEvent;
import com.msetransport.copilot.chat.CopilotService;
import com.msetransport.copilot.config.CopilotProperties;
import com.msetransport.copilot.config.CopilotToolbox;
import com.msetransport.copilot.guard.GroundingGuard;
import com.msetransport.copilot.security.Caller;
import com.msetransport.copilot.security.ProblemDetailAuthHandler;
import com.msetransport.copilot.security.SecurityConfig;
import com.msetransport.copilot.support.TestTokens;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import reactor.core.publisher.Flux;

/** Controllers + the real SecurityFilterChain and JWT decoder, with the copilot itself mocked. */
@WebMvcTest(controllers = {ChatController.class, InfoController.class, ConversationController.class})
@Import({SecurityConfig.class, ProblemDetailAuthHandler.class})
@ActiveProfiles("test")
class WebLayerTest {

    @Autowired
    private MockMvc mvc;
    @MockitoBean
    private CopilotService copilotService;
    @MockitoBean
    private ChatMemory chatMemory;
    @MockitoBean
    private CopilotProperties properties;
    @MockitoBean
    private CopilotToolbox toolbox;

    private static final String QUESTION = "{\"message\":\"Which courier owes me the most?\"}";

    @BeforeEach
    void setUp() {
        when(copilotService.answer(any(), any())).thenReturn(new ChatAnswer("c1", "demo", "en", "answer", List.of(), List.of(),
                null, new GroundingGuard.Verification(true, 0, List.of()), null, 5));
    }

    @Test
    void noTokenIs401ProblemDetail() throws Exception {
        mvc.perform(post("/api/copilot/chat").contentType(MediaType.APPLICATION_JSON).content(QUESTION))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void rejectsForgedExpiredForeignAndRefreshTokens() throws Exception {
        Instant future = Instant.now().plusSeconds(600);
        List<String> bad = List.of(
                "not-a-jwt",
                TestTokens.token("atlas", 2, "MERCHANT", 1L, "access", TestTokens.ISSUER, TestTokens.SECRET, Instant.now().minusSeconds(120)),
                TestTokens.token("atlas", 2, "MERCHANT", 1L, "access", "someone-else", TestTokens.SECRET, future),
                TestTokens.token("atlas", 2, "MERCHANT", 1L, "access", TestTokens.ISSUER, "another-secret-that-is-long-enough-0123456789", future),
                TestTokens.token("atlas", 2, "MERCHANT", 1L, "refresh", TestTokens.ISSUER, TestTokens.SECRET, future));
        for (String token : bad) {
            mvc.perform(post("/api/copilot/chat").header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON).content(QUESTION))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Test
    void validTokenReachesTheCopilotAsTheCaller() throws Exception {
        mvc.perform(post("/api/copilot/chat").header("Authorization", "Bearer " + TestTokens.atlas())
                        .contentType(MediaType.APPLICATION_JSON).content(QUESTION))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("answer"));
        ArgumentCaptor<Caller> caller = ArgumentCaptor.forClass(Caller.class);
        verify(copilotService).answer(caller.capture(), any(ChatRequest.class));
        assertThat(caller.getValue().username()).isEqualTo("atlas");
        assertThat(caller.getValue().merchantId()).isEqualTo(1L);
        assertThat(caller.getValue().token()).isEqualTo(TestTokens.atlas());
        assertThat(caller.getValue().toString()).doesNotContain(TestTokens.atlas());
    }

    @Test
    void staffTokensHaveNoMerchant() throws Exception {
        mvc.perform(post("/api/copilot/chat").header("Authorization", "Bearer " + TestTokens.ops())
                .contentType(MediaType.APPLICATION_JSON).content(QUESTION)).andExpect(status().isOk());
        ArgumentCaptor<Caller> caller = ArgumentCaptor.forClass(Caller.class);
        verify(copilotService).answer(caller.capture(), any());
        assertThat(caller.getValue().isStaff()).isTrue();
        assertThat(caller.getValue().merchantId()).isNull();
    }

    @Test
    void validatesTheQuestion() throws Exception {
        for (String body : List.of("{\"message\":\"  \"}", "{\"message\":\"hi\",\"conversationId\":\"../etc\"}",
                "{\"message\":\"hi\",\"language\":\"de\"}", "{\"message\":\"" + "x".repeat(2001) + "\"}")) {
            mvc.perform(post("/api/copilot/chat").header("Authorization", "Bearer " + TestTokens.atlas())
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.type").value("https://copilot.msetransport.com/problems/invalid-request"));
        }
    }

    @Test
    void streamsServerSentEvents() throws Exception {
        when(copilotService.stream(any(), any())).thenReturn(Flux.just(
                new CopilotEvent.Meta("c1", "demo", "scripted-offline", "en"), new CopilotEvent.Token("Hello "),
                new CopilotEvent.Done("c1", "Hello", 3)));
        MvcResult started = mvc.perform(post("/api/copilot/chat/stream").header("Authorization", "Bearer " + TestTokens.atlas())
                        .contentType(MediaType.APPLICATION_JSON).content(QUESTION))
                .andExpect(request().asyncStarted()).andReturn();
        String body = mvc.perform(asyncDispatch(started))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", org.hamcrest.Matchers.startsWith("text/event-stream")))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).contains("event:meta").contains("event:token").contains("data:{\"text\":\"Hello \"}").contains("event:done");
    }

    @Test
    void infoIsPublic() throws Exception {
        when(properties.mode()).thenReturn(CopilotProperties.CopilotMode.DEMO);
        when(properties.activeModel()).thenReturn("scripted-offline");
        when(properties.deskBaseUrl()).thenReturn("http://desk");
        when(toolbox.names()).thenReturn(List.of("getCourierBalances"));
        mvc.perform(get("/api/copilot/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("demo"))
                .andExpect(jsonPath("$.model").value("scripted-offline"))
                .andExpect(jsonPath("$.tools[0]").value("getCourierBalances"))
                .andExpect(jsonPath("$.description").value(org.hamcrest.Matchers.containsString("no API key")));
        when(properties.mode()).thenReturn(CopilotProperties.CopilotMode.ANTHROPIC);
        mvc.perform(get("/api/copilot/info")).andExpect(jsonPath("$.mode").value("anthropic"))
                .andExpect(jsonPath("$.description").value(org.hamcrest.Matchers.containsString("Claude")));
    }

    @Test
    void conversationsAreScopedToTheCaller() throws Exception {
        when(chatMemory.get("atlas:c1")).thenReturn(List.of(new UserMessage("q"), new AssistantMessage("a")));
        mvc.perform(get("/api/copilot/conversations/c1").header("Authorization", "Bearer " + TestTokens.atlas()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].role").value("user"))
                .andExpect(jsonPath("$[1].text").value("a"));
        mvc.perform(delete("/api/copilot/conversations/c1").header("Authorization", "Bearer " + TestTokens.atlas()))
                .andExpect(status().isNoContent());
        verify(chatMemory).clear(eq("atlas:c1"));
        mvc.perform(get("/api/copilot/conversations/bad$id").header("Authorization", "Bearer " + TestTokens.atlas()))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/copilot/conversations/c1")).andExpect(status().isUnauthorized());
    }

    @Test
    void unexpectedErrorsAre500WithoutDetails() throws Exception {
        when(copilotService.answer(any(), any())).thenThrow(new IllegalStateException("db password is hunter2"));
        mvc.perform(post("/api/copilot/chat").header("Authorization", "Bearer " + TestTokens.atlas())
                        .contentType(MediaType.APPLICATION_JSON).content(QUESTION))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.detail").value("Something went wrong on our side."));
    }

    @Test
    void propertiesRecordHelpers() {
        CopilotProperties p = new CopilotProperties(CopilotProperties.CopilotMode.ANTHROPIC, "claude-opus-5-5",
                new CopilotProperties.Settlement("u", Duration.ofSeconds(1), Duration.ofSeconds(1)), "d", 2, 1, 10,
                new CopilotProperties.Demo(Duration.ZERO));
        assertThat(p.activeModel()).isEqualTo("claude-opus-5-5");
        assertThat(CopilotProperties.CopilotMode.DEMO.label()).isEqualTo("demo");
    }
}
