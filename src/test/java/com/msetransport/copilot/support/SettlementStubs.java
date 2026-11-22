package com.msetransport.copilot.support;

import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.MappingBuilder;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * Plays the settlement platform (project 1) for the tests. The JSON fixtures were captured from
 * the real API running the demo data, one folder per user. Every stub requires that user's exact
 * bearer token, so a test also proves the copilot forwards the caller's token (and only that one).
 */
public final class SettlementStubs {

    private static final String EMPTY_PAGE = "{\"content\":[],\"page\":0,\"size\":100,\"totalElements\":0,\"totalPages\":0}";
    private static final String NOT_FOUND = "{\"type\":\"https://settle.msetransport.com/problems/not-found\","
            + "\"title\":\"Resource not found\",\"status\":404}";

    private SettlementStubs() {
    }

    public static void registerAll(WireMockServer wm) {
        register(wm, "atlas", TestTokens.atlas(), true);
        register(wm, "medina", TestTokens.medina(), false);
        register(wm, "ops", TestTokens.ops(), true);
        wm.stubFor(get(urlPathEqualTo("/api/orders")).atPriority(1)
                .withHeader("Authorization", equalTo("Bearer " + TestTokens.atlas()))
                .withQueryParam("status", equalTo("DELIVERED"))
                .withQueryParam("city", equalTo("Casablanca"))
                .withQueryParam("minAmount", equalTo("300"))
                .willReturn(json(fixture("atlas", "orders-casablanca-delivered-300"))));
    }

    private static void register(WireMockServer wm, String user, String token, boolean hasStatementOne) {
        wm.stubFor(auth(get(urlPathEqualTo("/api/overview")), token).willReturn(json(fixture(user, "overview"))));
        wm.stubFor(auth(get(urlPathEqualTo("/api/couriers")), token).willReturn(json(fixture(user, "couriers"))));
        wm.stubFor(auth(get(urlPathEqualTo("/api/couriers/1/balance")), token).willReturn(json(fixture(user, "balance-1"))));
        wm.stubFor(auth(get(urlPathEqualTo("/api/couriers/2/balance")), token).willReturn(json(fixture(user, "balance-2"))));
        wm.stubFor(auth(get(urlPathMatching("/api/couriers/\\d+/balance")), token).atPriority(9)
                .willReturn(aResponse().withStatus(404).withHeader("Content-Type", "application/problem+json").withBody(NOT_FOUND)));
        wm.stubFor(auth(get(urlPathEqualTo("/api/disputes")), token).atPriority(5)
                .withQueryParam("status", absent()).willReturn(json(fixture(user, "disputes"))));
        wm.stubFor(auth(get(urlPathEqualTo("/api/disputes")), token).atPriority(5)
                .withQueryParam("status", equalTo("OPEN")).willReturn(json(fixture(user, "disputes-open"))));
        wm.stubFor(auth(get(urlPathEqualTo("/api/disputes")), token).atPriority(6).willReturn(json(EMPTY_PAGE)));
        wm.stubFor(auth(get(urlPathEqualTo("/api/statements")), token).willReturn(json(fixture(user, "statements"))));
        wm.stubFor(auth(get(urlPathEqualTo("/api/orders")), token).atPriority(5).willReturn(json(fixture(user, "orders"))));
        wm.stubFor(auth(get(urlPathMatching("/api/statements/\\d+/reconciliation")), token).atPriority(9)
                .willReturn(aResponse().withStatus(404).withHeader("Content-Type", "application/problem+json").withBody(NOT_FOUND)));
        if (hasStatementOne) {
            wm.stubFor(auth(get(urlPathEqualTo("/api/statements/1/reconciliation")), token).atPriority(5)
                    .willReturn(json(fixture(user, "reconciliation-1"))));
        }
    }

    private static MappingBuilder auth(MappingBuilder builder, String token) {
        return builder.withHeader("Authorization", equalTo("Bearer " + token));
    }

    private static com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder json(String body) {
        return aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody(body);
    }

    public static String fixture(String user, String name) {
        String path = "/wiremock/fixtures/" + user + "/" + name + ".json";
        try (InputStream in = SettlementStubs.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalArgumentException("Missing fixture " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
}
