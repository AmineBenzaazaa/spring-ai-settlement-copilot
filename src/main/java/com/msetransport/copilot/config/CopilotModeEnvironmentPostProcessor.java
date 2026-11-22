package com.benzaazaa.copilot.config;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.util.StringUtils;

/**
 * Decides, before any bean exists, which chat model the app runs with.
 *
 * <ul>
 *   <li>{@code copilot.mode=demo}: the deterministic offline model, Spring AI's Anthropic
 *       auto-configuration is switched off ({@code spring.ai.model.chat=none}).</li>
 *   <li>{@code copilot.mode=anthropic}: Claude through Spring AI; requires an API key.</li>
 *   <li>{@code copilot.mode=auto} (default): anthropic when {@code ANTHROPIC_API_KEY} is set, demo otherwise.</li>
 * </ul>
 *
 * Embeddings always come from the local {@code HashingEmbeddingModel}, so the Spring AI
 * embedding auto-configuration is disabled too.
 */
public class CopilotModeEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    static final String SOURCE_NAME = "copilotMode";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String requested = environment.getProperty("copilot.mode", "auto").trim().toLowerCase(Locale.ROOT);
        boolean hasKey = StringUtils.hasText(environment.getProperty("spring.ai.anthropic.api-key"));
        String mode = switch (requested) {
            case "demo" -> "demo";
            case "anthropic" -> {
                if (!hasKey) {
                    throw new IllegalStateException(
                            "copilot.mode=anthropic needs ANTHROPIC_API_KEY. Set it in .env or use copilot.mode=demo");
                }
                yield "anthropic";
            }
            case "auto" -> hasKey ? "anthropic" : "demo";
            default -> throw new IllegalStateException("copilot.mode must be auto, demo or anthropic, not '" + requested + "'");
        };
        Map<String, Object> props = new HashMap<>();
        props.put("copilot.mode", mode);
        props.put("spring.ai.model.chat", "demo".equals(mode) ? "none" : "anthropic");
        props.put("spring.ai.model.embedding", "none");
        environment.getPropertySources().addFirst(new MapPropertySource(SOURCE_NAME, props));
    }

    @Override
    public int getOrder() {
        // after application.yml and profile files are loaded
        return Ordered.LOWEST_PRECEDENCE;
    }
}
