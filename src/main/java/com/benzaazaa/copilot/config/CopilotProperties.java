package com.benzaazaa.copilot.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Typed configuration bound from {@code copilot.*}.
 *
 * @param mode           {@link CopilotMode#DEMO} (offline scripted model) or {@link CopilotMode#ANTHROPIC};
 *                       resolved from {@code auto} at startup by {@link CopilotModeEnvironmentPostProcessor}
 * @param deskBaseUrl    where the Settlement Desk web UI lives, used to build source links
 * @param maxToolRounds  safety net against a model that keeps calling tools forever
 */
@Validated
@ConfigurationProperties(prefix = "copilot")
public record CopilotProperties(
        @NotNull CopilotMode mode,
        @NotBlank String model,
        @Valid @NotNull Settlement settlement,
        @NotBlank String deskBaseUrl,
        @Min(2) @Max(100) int memoryMaxMessages,
        @Min(1) @Max(10) int maxToolRounds,
        @Min(10) @Max(10_000) int maxQuestionLength,
        @Valid @NotNull Demo demo) {

    /** The model id shown to users: the Claude model, or the offline scripted model in demo mode. */
    public String activeModel() {
        return mode == CopilotMode.DEMO ? "scripted-offline" : model;
    }

    /** @param baseUrl the settlement platform's REST API (project 1) */
    public record Settlement(@NotBlank String baseUrl, @NotNull Duration connectTimeout, @NotNull Duration readTimeout) {
    }

    /** @param tokenDelay pause between streamed words so the demo "types" like a real model */
    public record Demo(@NotNull Duration tokenDelay) {
    }

    public enum CopilotMode {
        DEMO, ANTHROPIC;

        public String label() {
            return name().toLowerCase();
        }
    }
}
