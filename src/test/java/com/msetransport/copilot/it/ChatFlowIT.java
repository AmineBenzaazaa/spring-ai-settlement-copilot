package com.benzaazaa.copilot.it;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.benzaazaa.copilot.chat.ChatAnswer;
import com.benzaazaa.copilot.support.AbstractIntegrationTest;
import com.benzaazaa.copilot.support.TestTokens;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** The whole app: HTTP -> security -> agent loop -> tools -> (WireMock) settlement API -> pgvector memory. */
class ChatFlowIT extends AbstractIntegrationTest {

    @Test
    void answersWithExactNumbersSourcesAndAChart() {
        ChatAnswer answer = rest.postForObject("/api/copilot/chat", chat(TestTokens.atlas(), body("Which courier owes me the most?")),
                ChatAnswer.class);
        assertThat(answer.answer()).startsWith("**Rapido Express** owes you the most: **4315.00 MAD**")
                .contains("Speedex Colis: 530.50 MAD").contains("4845.50 MAD");
        assertThat(answer.toolCalls()).singleElement().satisfies(t -> {
            assertThat(t.tool()).isEqualTo("getCourierBalances");
            assertThat(t.status()).isEqualTo("OK");
            assertThat(t.rows()).isEqualTo(2);
        });
        assertThat(answer.sources()).extracting("label").containsExactly("Rapido Express", "Speedex Colis");
        assertThat(answer.chart().type()).isEqualTo("bar");
        assertThat(answer.verification().grounded()).isTrue();
        assertThat(answer.mode()).isEqualTo("demo");
        // the caller's own token went to the platform
        SETTLEMENT.verify(getRequestedFor(urlPathEqualTo("/api/overview"))
                .withHeader("Authorization", equalTo("Bearer " + TestTokens.atlas())));
    }

    @Test
    void explainsAFlaggedStatementWithADeepLink() {
        ChatAnswer answer = rest.postForObject("/api/copilot/chat", chat(TestTokens.atlas(), body("Why was statement #1 flagged?")),
                ChatAnswer.class);
        assertThat(answer.answer()).contains("**3 of its 14 lines**").contains("SPX-100007").contains("-331.50 MAD");
        assertThat(answer.sources()).singleElement().satisfies(s -> assertThat(s.url()).isEqualTo("http://desk.test/statements/1"));
        assertThat(answer.chart().type()).isEqualTo("doughnut");
    }

    @Test
    void remembersTheConversationPerUser() {
        String conversation = "it-memory-1";
        rest.postForObject("/api/copilot/chat", chat(TestTokens.atlas(), body("How much does Speedex owe me?", conversation)),
                ChatAnswer.class);
        ChatAnswer followUp = rest.postForObject("/api/copilot/chat", chat(TestTokens.atlas(), body("And Rapido?", conversation)),
                ChatAnswer.class);
        assertThat(followUp.answer()).startsWith("**Rapido Express** still owes you **4315.00 MAD**");

        JsonNode history = exchange(TestTokens.atlas(), "/api/copilot/conversations/" + conversation, HttpMethod.GET).getBody();
        assertThat(history).hasSize(4);
        assertThat(history.get(0).path("text").asText()).isEqualTo("How much does Speedex owe me?");
        // same id, other user: nothing
        assertThat(exchange(TestTokens.medina(), "/api/copilot/conversations/" + conversation, HttpMethod.GET).getBody()).isEmpty();

        assertThat(exchange(TestTokens.atlas(), "/api/copilot/conversations/" + conversation, HttpMethod.DELETE).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(exchange(TestTokens.atlas(), "/api/copilot/conversations/" + conversation, HttpMethod.GET).getBody()).isEmpty();
    }

    @Test
    void infoAndHealthArePublic() {
        JsonNode info = rest.getForObject("/api/copilot/info", JsonNode.class);
        assertThat(info.path("mode").asText()).isEqualTo("demo");
        assertThat(info.path("tools")).hasSize(9);
        assertThat(rest.getForObject("/actuator/health", String.class)).contains("UP");
        assertThat(rest.getForEntity("/v3/api-docs", String.class).getBody()).contains("/api/copilot/chat/stream");
    }

    private ResponseEntity<JsonNode> exchange(String token, String path, HttpMethod method) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return rest.exchange(path, method, new HttpEntity<>(headers), JsonNode.class);
    }
}
