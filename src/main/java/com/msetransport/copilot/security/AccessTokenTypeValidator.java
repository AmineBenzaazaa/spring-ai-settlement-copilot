package com.benzaazaa.copilot.security;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/** The platform also issues refresh tokens with the same key: only {@code typ=access} may call the copilot. */
public class AccessTokenTypeValidator implements OAuth2TokenValidator<Jwt> {

    static final String CLAIM_TYPE = "typ";
    private static final OAuth2Error WRONG_TYPE = new OAuth2Error("invalid_token", "Not an access token", null);

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        return "access".equals(jwt.getClaimAsString(CLAIM_TYPE))
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(WRONG_TYPE);
    }
}
