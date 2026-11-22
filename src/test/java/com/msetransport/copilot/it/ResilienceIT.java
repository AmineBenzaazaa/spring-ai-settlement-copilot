package com.msetransport.copilot.it;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.msetransport.copilot.chat.ChatAnswer;
import com.msetransport.copilot.support.AbstractIntegrationTest;
import com.msetransport.copilot.support.SettlementStubs;
import com.msetransport.copilot.support.TestTokens;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Retries on 5xx, gives up politely when the platform stays down, and fails fast when the circuit is open. */
class ResilienceIT extends AbstractIntegrationTest {

    @Autowired
    private CircuitBreakerRegistry circuitBreakers;

    @AfterEach
    void closeCircuit() {
        circuitBreakers.circuitBreaker("settlement").reset();
    }

    @Test
    void retriesTransientFailures() {
        String auth = "Bearer " + TestTokens.atlas();
        SETTLEMENT.stubFor(get(urlPathEqualTo("/api/overview")).atPriority(1).withHeader("Authorization", equalTo(auth))
                .inScenario("flaky").whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse().withStatus(503)).willSetStateTo("second"));
        SETTLEMENT.stubFor(get(urlPathEqualTo("/api/overview")).atPriority(1).withHeader("Authorization", equalTo(auth))
                .inScenario("flaky").whenScenarioStateIs("second")
                .willReturn(aResponse().withStatus(502)).willSetStateTo("healthy"));
        SETTLEMENT.stubFor(get(urlPathEqualTo("/api/overview")).atPriority(1).withHeader("Authorization", equalTo(auth))
                .inScenario("flaky").whenScenarioStateIs("healthy")
                .willReturn(okJson(SettlementStubs.fixture("atlas", "overview"))));

        ChatAnswer answer = rest.postForObject("/api/copilot/chat", chat(TestTokens.atlas(), body("Give me an overview")), ChatAnswer.class);
        assertThat(answer.answer()).contains("4845.50 MAD");
        SETTLEMENT.verify(3, getRequestedFor(urlPathEqualTo("/api/overview")));
    }

    @Test
    void saysSoWhenThePlatformStaysDown() {
        SETTLEMENT.stubFor(get(urlPathEqualTo("/api/overview")).atPriority(1).willReturn(aResponse().withStatus(500)));
        ChatAnswer answer = rest.postForObject("/api/copilot/chat", chat(TestTokens.atlas(), body("Give me an overview")), ChatAnswer.class);
        assertThat(answer.toolCalls()).singleElement().satisfies(t -> assertThat(t.status()).isEqualTo("UNAVAILABLE"));
        assertThat(answer.answer()).contains("isn't reachable").doesNotContainPattern("\\d+\\.\\d{2}");
        SETTLEMENT.verify(3, getRequestedFor(urlPathEqualTo("/api/overview")));
    }

    @Test
    void openCircuitFailsFastWithoutCallingThePlatform() {
        circuitBreakers.circuitBreaker("settlement").transitionToOpenState();
        ChatAnswer answer = rest.postForObject("/api/copilot/chat", chat(TestTokens.atlas(), body("Give me an overview")), ChatAnswer.class);
        assertThat(answer.answer()).contains("isn't reachable");
        SETTLEMENT.verify(0, getRequestedFor(urlPathEqualTo("/api/overview")));
        assertThat(circuitBreakers.circuitBreaker("settlement").getState()).isEqualTo(CircuitBreaker.State.OPEN);
    }

    @Test
    void notFoundIsNotRetried() {
        rest.postForObject("/api/copilot/chat", chat(TestTokens.atlas(), body("Why was statement #99 flagged?")), ChatAnswer.class);
        SETTLEMENT.verify(1, getRequestedFor(urlPathEqualTo("/api/statements/99/reconciliation")));
    }
}
