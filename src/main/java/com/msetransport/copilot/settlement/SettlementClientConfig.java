package com.benzaazaa.copilot.settlement;

import com.benzaazaa.copilot.config.CopilotProperties;
import java.net.http.HttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
public class SettlementClientConfig {

    /**
     * Built from Spring Boot's {@link RestClient.Builder} (Jackson, observation) plus timeouts and
     * a status handler that turns HTTP errors into typed exceptions the resilience rules understand.
     */
    @Bean
    RestClient settlementRestClient(RestClient.Builder builder, CopilotProperties properties) {
        CopilotProperties.Settlement settlement = properties.settlement();
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(settlement.connectTimeout())
                .version(HttpClient.Version.HTTP_1_1)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(settlement.readTimeout());
        return builder.clone()
                .baseUrl(settlement.baseUrl())
                .requestFactory(requestFactory)
                .defaultStatusHandler(status -> status.isError(), (request, response) -> {
                    HttpStatus status = HttpStatus.resolve(response.getStatusCode().value());
                    String what = request.getMethod() + " " + request.getURI().getPath();
                    if (status == HttpStatus.NOT_FOUND) {
                        throw new SettlementApiException.NotFound(what + " -> 404");
                    }
                    if (status == HttpStatus.UNAUTHORIZED || status == HttpStatus.FORBIDDEN) {
                        throw new SettlementApiException.AccessDenied(what + " -> " + response.getStatusCode().value());
                    }
                    if (response.getStatusCode().is4xxClientError()) {
                        throw new SettlementApiException.BadRequest(what + " -> " + response.getStatusCode().value());
                    }
                    throw new SettlementApiException.Unavailable(what + " -> " + response.getStatusCode().value(), null);
                })
                .build();
    }
}
