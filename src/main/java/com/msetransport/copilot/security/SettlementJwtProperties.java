package com.msetransport.copilot.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * The settlement platform signs its access tokens with HS256. The copilot validates them with
 * the same shared secret and issuer, so a token from the platform's login works here unchanged.
 */
@Validated
@ConfigurationProperties(prefix = "copilot.jwt")
public record SettlementJwtProperties(
        @NotBlank @Size(min = 32, message = "must be at least 32 characters (set APP_JWT_SECRET)") String secret,
        @NotBlank String issuer) {
}
