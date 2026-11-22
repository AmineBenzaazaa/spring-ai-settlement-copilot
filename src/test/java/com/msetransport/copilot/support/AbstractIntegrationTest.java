package com.msetransport.copilot.support;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Full application on a random port, with a real PostgreSQL + pgvector (Testcontainers) and the
 * settlement platform played by WireMock. Both are started once per JVM (singleton pattern), so all
 * integration test classes share them and Spring's cached context.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    protected static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("copilot").withUsername("copilot").withPassword("copilot");

    protected static final WireMockServer SETTLEMENT = new WireMockServer(WireMockConfiguration.options().dynamicPort());

    static {
        POSTGRES.start();
        SETTLEMENT.start();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("copilot.settlement.base-url", SETTLEMENT::baseUrl);
    }

    @LocalServerPort
    protected int port;

    @Autowired
    protected TestRestTemplate rest;

    @BeforeEach
    void resetSettlementStubs() {
        SETTLEMENT.resetAll();
        SettlementStubs.registerAll(SETTLEMENT);
    }

    protected static HttpEntity<String> chat(String token, String json) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return new HttpEntity<>(json, headers);
    }

    protected static String body(String message) {
        return "{\"message\":" + com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.textNode(message) + "}";
    }

    protected static String body(String message, String conversationId) {
        return "{\"message\":" + com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.textNode(message)
                + ",\"conversationId\":\"" + conversationId + "\"}";
    }
}
