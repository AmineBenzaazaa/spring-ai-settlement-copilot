package com.msetransport.copilot.settlement;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.msetransport.copilot.config.CopilotProperties;
import com.msetransport.copilot.support.SettlementStubs;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import java.math.BigDecimal;
import java.time.Duration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

/** The HTTP contract with the settlement platform, against WireMock (no Spring context, no Docker). */
class SettlementApiClientTest {

    private static final WireMockServer WM = new WireMockServer(WireMockConfiguration.options().dynamicPort());
    private SettlementApiClient client;

    @BeforeAll
    static void start() {
        WM.start();
    }

    @AfterAll
    static void stop() {
        WM.stop();
    }

    @BeforeEach
    void setUp() {
        WM.resetAll();
        CopilotProperties props = new CopilotProperties(CopilotProperties.CopilotMode.DEMO, "m",
                new CopilotProperties.Settlement(WM.baseUrl(), Duration.ofSeconds(2), Duration.ofMillis(1500)),
                "http://desk", 20, 5, 2000, new CopilotProperties.Demo(Duration.ZERO));
        RestClient rest = new SettlementClientConfig().settlementRestClient(RestClient.builder(), props);
        client = new SettlementApiClient(rest);
    }

    @Test
    void forwardsTheCallersTokenAndParsesMoneyAsBigDecimal() {
        WM.stubFor(get(urlPathEqualTo("/api/overview")).willReturn(okJson(SettlementStubs.fixture("atlas", "overview"))));
        SettlementModels.Overview overview = client.overview("abc");
        assertThat(overview.balanceOwed()).isEqualByComparingTo(new BigDecimal("4845.50"));
        assertThat(overview.couriers()).hasSize(2);
        WM.verify(getRequestedFor(urlPathEqualTo("/api/overview")).withHeader("Authorization", equalTo("Bearer abc")));
    }

    @Test
    void sendsFiltersAsQueryParametersAndSkipsBlanks() {
        WM.stubFor(get(urlPathEqualTo("/api/disputes")).willReturn(okJson(SettlementStubs.fixture("atlas", "disputes"))));
        WM.stubFor(get(urlPathEqualTo("/api/orders")).willReturn(okJson(SettlementStubs.fixture("atlas", "orders"))));
        WM.stubFor(get(urlPathEqualTo("/api/statements")).willReturn(okJson(SettlementStubs.fixture("atlas", "statements"))));
        WM.stubFor(get(urlPathEqualTo("/api/couriers")).willReturn(okJson(SettlementStubs.fixture("atlas", "couriers"))));
        WM.stubFor(get(urlPathEqualTo("/api/couriers/1/balance")).willReturn(okJson(SettlementStubs.fixture("atlas", "balance-1"))));
        WM.stubFor(get(urlPathEqualTo("/api/statements/1/reconciliation"))
                .willReturn(okJson(SettlementStubs.fixture("atlas", "reconciliation-1"))));

        assertThat(client.disputes("t", "OPEN", 1L, null).totalElements()).isEqualTo(2);
        WM.verify(getRequestedFor(urlPathEqualTo("/api/disputes")).withQueryParam("status", equalTo("OPEN"))
                .withQueryParam("courierId", equalTo("1")).withQueryParam("sort", equalTo("claimedAmount,desc")));
        client.orders("t", new SettlementApiClient.OrderQuery("DELIVERED", " ", null, new BigDecimal("300"), null, null));
        WM.verify(getRequestedFor(urlPathEqualTo("/api/orders")).withQueryParam("status", equalTo("DELIVERED"))
                .withQueryParam("minAmount", equalTo("300")).withoutQueryParam("city"));
        assertThat(client.statements("t", null, null).content()).hasSize(1);
        assertThat(client.couriers("t", "spx").content()).hasSize(2);
        assertThat(client.courierBalance("t", 1).balanceOwed()).isEqualByComparingTo("530.50");
        SettlementModels.ReconciliationReport report = client.reconciliation("t", 1, null);
        assertThat(report.summary().counts()).containsEntry("AMOUNT_MISMATCH", 1L);
        assertThat(report.results()).hasSize(14);
    }

    @Test
    void mapsHttpErrorsToTypedExceptions() {
        WM.stubFor(get(urlPathEqualTo("/api/statements/9/reconciliation")).willReturn(aResponse().withStatus(404)));
        WM.stubFor(get(urlPathEqualTo("/api/overview")).willReturn(aResponse().withStatus(401)));
        WM.stubFor(get(urlPathEqualTo("/api/statements")).willReturn(aResponse().withStatus(400)));
        WM.stubFor(get(urlPathEqualTo("/api/disputes")).willReturn(aResponse().withStatus(503)));
        WM.stubFor(get(urlPathEqualTo("/api/couriers")).willReturn(aResponse().withStatus(403)));
        assertThatThrownBy(() -> client.reconciliation("t", 9, null)).isInstanceOf(SettlementApiException.NotFound.class);
        assertThatThrownBy(() -> client.overview("t")).isInstanceOf(SettlementApiException.AccessDenied.class);
        assertThatThrownBy(() -> client.couriers("t", null)).isInstanceOf(SettlementApiException.AccessDenied.class);
        assertThatThrownBy(() -> client.statements("t", null, null)).isInstanceOf(SettlementApiException.BadRequest.class);
        assertThatThrownBy(() -> client.disputes("t", null, null, null)).isInstanceOf(SettlementApiException.Unavailable.class);
    }

    @Test
    void slowResponsesTimeOut() {
        WM.stubFor(get(urlPathEqualTo("/api/overview")).willReturn(okJson("{}").withFixedDelay(3000)));
        assertThatThrownBy(() -> client.overview("t")).isInstanceOf(SettlementApiException.Unavailable.class)
                .hasMessage("Settlement API did not answer in time");
    }

    @Test
    void deskLinks() {
        CopilotProperties props = new CopilotProperties(CopilotProperties.CopilotMode.DEMO, "m",
                new CopilotProperties.Settlement("http://a", Duration.ofSeconds(1), Duration.ofSeconds(1)),
                "http://desk:3201/", 20, 5, 2000, new CopilotProperties.Demo(Duration.ZERO));
        DeskLinks links = new DeskLinks(props);
        assertThat(links.statement(4)).isEqualTo("http://desk:3201/statements/4");
        assertThat(links.disputes()).isEqualTo("http://desk:3201/disputes");
        assertThat(links.couriers()).isEqualTo("http://desk:3201/couriers");
        assertThat(links.overview()).isEqualTo("http://desk:3201/");
        assertThat(links.orders(null)).isEqualTo("http://desk:3201/orders");
        assertThat(links.orders("SPX 1")).isEqualTo("http://desk:3201/orders?q=SPX+1");
    }
}
