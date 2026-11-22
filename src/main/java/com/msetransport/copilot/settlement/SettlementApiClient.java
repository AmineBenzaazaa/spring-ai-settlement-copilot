package com.benzaazaa.copilot.settlement;

import com.benzaazaa.copilot.settlement.SettlementModels.Courier;
import com.benzaazaa.copilot.settlement.SettlementModels.CourierBalance;
import com.benzaazaa.copilot.settlement.SettlementModels.Dispute;
import com.benzaazaa.copilot.settlement.SettlementModels.Order;
import com.benzaazaa.copilot.settlement.SettlementModels.Overview;
import com.benzaazaa.copilot.settlement.SettlementModels.Page;
import com.benzaazaa.copilot.settlement.SettlementModels.ReconciliationReport;
import com.benzaazaa.copilot.settlement.SettlementModels.Statement;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import java.math.BigDecimal;
import java.util.function.Function;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriBuilder;

/**
 * Typed HTTP client for the settlement platform (project 1). Every call carries the caller's own
 * bearer token, so the platform applies its tenant rules: the copilot cannot see more than the
 * user could see in the Settlement Desk.
 *
 * <p>Resilience: connect/read timeouts on the {@link RestClient}, {@code @Retry} with exponential
 * backoff for 5xx/timeouts only, and a {@code @CircuitBreaker} so a down platform fails fast.
 */
@Component
public class SettlementApiClient {

    static final String RESILIENCE = "settlement";

    private final RestClient restClient;

    public SettlementApiClient(RestClient settlementRestClient) {
        this.restClient = settlementRestClient;
    }

    @Retry(name = RESILIENCE)
    @CircuitBreaker(name = RESILIENCE)
    public Overview overview(String token) {
        return get(token, b -> b.path("/api/overview").build(), new ParameterizedTypeReference<>() { });
    }

    @Retry(name = RESILIENCE)
    @CircuitBreaker(name = RESILIENCE)
    public Page<Courier> couriers(String token, String query) {
        return get(token, b -> b.path("/api/couriers").queryParamIfPresent("q", optional(query))
                .queryParam("size", 100).build(), new ParameterizedTypeReference<>() { });
    }

    @Retry(name = RESILIENCE)
    @CircuitBreaker(name = RESILIENCE)
    public CourierBalance courierBalance(String token, long courierId) {
        return get(token, b -> b.path("/api/couriers/{id}/balance").build(courierId), new ParameterizedTypeReference<>() { });
    }

    @Retry(name = RESILIENCE)
    @CircuitBreaker(name = RESILIENCE)
    public Page<Dispute> disputes(String token, String status, Long courierId, Long statementId) {
        return get(token, b -> b.path("/api/disputes")
                .queryParamIfPresent("status", optional(status))
                .queryParamIfPresent("courierId", optional(courierId))
                .queryParamIfPresent("statementId", optional(statementId))
                .queryParam("size", 100).queryParam("sort", "claimedAmount,desc").build(),
                new ParameterizedTypeReference<>() { });
    }

    @Retry(name = RESILIENCE)
    @CircuitBreaker(name = RESILIENCE)
    public Page<Statement> statements(String token, String status, Long courierId) {
        return get(token, b -> b.path("/api/statements")
                .queryParamIfPresent("status", optional(status))
                .queryParamIfPresent("courierId", optional(courierId))
                .queryParam("size", 50).build(), new ParameterizedTypeReference<>() { });
    }

    @Retry(name = RESILIENCE)
    @CircuitBreaker(name = RESILIENCE)
    public ReconciliationReport reconciliation(String token, long statementId, String outcome) {
        return get(token, b -> b.path("/api/statements/{id}/reconciliation")
                .queryParamIfPresent("outcome", optional(outcome)).build(statementId),
                new ParameterizedTypeReference<>() { });
    }

    @Retry(name = RESILIENCE)
    @CircuitBreaker(name = RESILIENCE)
    public Page<Order> orders(String token, OrderQuery query) {
        return get(token, b -> b.path("/api/orders")
                .queryParamIfPresent("status", optional(query.status()))
                .queryParamIfPresent("city", optional(query.city()))
                .queryParamIfPresent("courierId", optional(query.courierId()))
                .queryParamIfPresent("minAmount", optional(query.minAmount()))
                .queryParamIfPresent("maxAmount", optional(query.maxAmount()))
                .queryParamIfPresent("q", optional(query.text()))
                .queryParam("size", 50).queryParam("sort", "amountDue,desc").build(),
                new ParameterizedTypeReference<>() { });
    }

    /** Filters accepted by {@code GET /api/orders}; {@code null} means "no filter". */
    public record OrderQuery(String status, String city, Long courierId, BigDecimal minAmount, BigDecimal maxAmount,
                             String text) {
    }

    private <T> T get(String token, Function<UriBuilder, java.net.URI> uri, ParameterizedTypeReference<T> type) {
        try {
            return restClient.get()
                    .uri(uri)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .retrieve()
                    .body(type);
        } catch (ResourceAccessException ex) {
            throw new SettlementApiException.Unavailable("Settlement API did not answer in time", ex);
        }
    }

    private static <T> java.util.Optional<T> optional(T value) {
        if (value instanceof String s && s.isBlank()) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.ofNullable(value);
    }
}
