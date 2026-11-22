package com.benzaazaa.copilot.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * @param conversationId optional; a new one is created when absent. Scoped to the caller, so two
 *                       users sending the same id never share memory.
 * @param language       {@code en}, {@code fr} or empty for auto-detection
 */
public record ChatRequest(
        @Pattern(regexp = "[A-Za-z0-9-]{1,64}", message = "letters, digits and '-' only (max 64)") String conversationId,
        @NotBlank @Size(max = 2000) String message,
        @Pattern(regexp = "en|fr|auto|", message = "must be en, fr or auto") String language) {
}
