package com.msetransport.copilot.settlement;

import com.msetransport.copilot.config.CopilotProperties;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Component;

/** Builds deep links into the Settlement Desk web UI so every answer can point at its source rows. */
@Component
public class DeskLinks {

    private final String base;

    public DeskLinks(CopilotProperties properties) {
        String url = properties.deskBaseUrl();
        this.base = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    public String statement(long id) {
        return base + "/statements/" + id;
    }

    public String disputes() {
        return base + "/disputes";
    }

    public String couriers() {
        return base + "/couriers";
    }

    public String overview() {
        return base + "/";
    }

    public String orders(String query) {
        return query == null || query.isBlank() ? base + "/orders"
                : base + "/orders?q=" + URLEncoder.encode(query, StandardCharsets.UTF_8);
    }
}
