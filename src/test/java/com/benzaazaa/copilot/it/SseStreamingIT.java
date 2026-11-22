package com.benzaazaa.copilot.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.benzaazaa.copilot.support.AbstractIntegrationTest;
import com.benzaazaa.copilot.support.TestTokens;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Reads the real SSE stream over HTTP, line by line, the way the Angular client does. */
class SseStreamingIT extends AbstractIntegrationTest {

    private final ObjectMapper mapper = new ObjectMapper();

    record Event(String name, JsonNode data) {
    }

    @Test
    void streamsToolTraceTokensAndFinalEventsInOrder() throws Exception {
        HttpResponse<Stream<String>> response = post(TestTokens.atlas(),
                "{\"message\":\"How much does Speedex owe me?\",\"conversationId\":\"sse-1\"}");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Type").orElse("")).startsWith("text/event-stream");

        List<Event> events = parse(response.body());
        List<String> names = events.stream().map(Event::name).toList();
        assertThat(names.getFirst()).isEqualTo("meta");
        assertThat(names.subList(1, 3)).containsExactly("tool", "tool");
        assertThat(names).containsSubsequence("tool", "token", "chart", "sources", "verification", "done");
        assertThat(names.stream().filter("token"::equals).count()).isGreaterThan(10);

        assertThat(events.get(1).data().path("trace").path("tool").asText()).isEqualTo("findCourier");
        assertThat(events.get(2).data().path("trace").path("tool").asText()).isEqualTo("getCourierBalance");
        String streamed = events.stream().filter(e -> e.name().equals("token")).map(e -> e.data().path("text").asText())
                .reduce("", String::concat);
        Event done = events.getLast();
        assertThat(done.name()).isEqualTo("done");
        assertThat(done.data().path("answer").asText()).isEqualTo(streamed.strip());
        assertThat(streamed).contains("**530.50 MAD**");
    }

    @Test
    void streamWithoutTokenIs401() throws Exception {
        assertThat(post(null, "{\"message\":\"hi\"}").statusCode()).isEqualTo(401);
    }

    private HttpResponse<Stream<String>> post(String token, String json) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/copilot/chat/stream"))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(json));
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return HttpClient.newHttpClient().send(request.build(), HttpResponse.BodyHandlers.ofLines());
    }

    private List<Event> parse(Stream<String> lines) throws Exception {
        List<Event> events = new ArrayList<>();
        String name = null;
        for (String line : (Iterable<String>) lines::iterator) {
            if (line.startsWith("event:")) {
                name = line.substring(6).trim();
            } else if (line.startsWith("data:")) {
                events.add(new Event(name, mapper.readTree(line.substring(5))));
            }
        }
        return events;
    }
}
