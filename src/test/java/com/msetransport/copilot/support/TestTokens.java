package com.msetransport.copilot.support;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

/**
 * Mints tokens shaped exactly like the settlement platform's (HS256, issuer, typ, uid, role, mid),
 * signed with the test secret from application-test.yml.
 */
public final class TestTokens {

    public static final String SECRET = "test-only-secret-for-the-copilot-suite-0123456789";
    public static final String ISSUER = "cod-settlement";

    // Minted once: WireMock stubs match the exact bearer string, so it must stay stable for the JVM.
    private static final String ATLAS = merchant("atlas", 2L, 1L);
    private static final String MEDINA = merchant("medina", 3L, 2L);
    private static final String OPS = token("ops", 1L, "STAFF", null, "access", ISSUER, SECRET,
            Instant.now().plusSeconds(86_400));

    private TestTokens() {
    }

    public static String atlas() {
        return ATLAS;
    }

    public static String medina() {
        return MEDINA;
    }

    public static String ops() {
        return OPS;
    }

    public static String merchant(String username, long userId, long merchantId) {
        return token(username, userId, "MERCHANT", merchantId, "access", ISSUER, SECRET, Instant.now().plusSeconds(86_400));
    }

    public static String token(String username, long userId, String role, Long merchantId, String type, String issuer,
                               String secret, Instant expiresAt) {
        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                .subject(username)
                .issuer(issuer)
                .claim("typ", type)
                .claim("uid", userId)
                .claim("role", role)
                .issueTime(Date.from(Instant.now().minusSeconds(5)))
                .expirationTime(Date.from(expiresAt));
        if (merchantId != null) {
            claims.claim("mid", merchantId);
        }
        try {
            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims.build());
            jwt.sign(new MACSigner(secret.getBytes(StandardCharsets.UTF_8)));
            return jwt.serialize();
        } catch (JOSEException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
