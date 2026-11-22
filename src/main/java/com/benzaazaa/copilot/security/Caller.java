package com.benzaazaa.copilot.security;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Who is asking, rebuilt from the validated JWT. {@code token} is the raw bearer token, forwarded
 * unchanged to the settlement API so that the platform itself enforces tenant isolation.
 *
 * @param merchantId {@code null} for staff users
 */
public record Caller(String username, String role, Long merchantId, String token) {

    public static Caller from(JwtAuthenticationToken authentication) {
        Jwt jwt = authentication.getToken();
        Number mid = jwt.getClaim("mid");
        return new Caller(jwt.getSubject(), jwt.getClaimAsString("role"), mid == null ? null : mid.longValue(),
                jwt.getTokenValue());
    }

    public boolean isStaff() {
        return "STAFF".equals(role);
    }

    /** Never print the token. */
    @Override
    public String toString() {
        return "Caller[username=" + username + ", role=" + role + ", merchantId=" + merchantId + "]";
    }
}
