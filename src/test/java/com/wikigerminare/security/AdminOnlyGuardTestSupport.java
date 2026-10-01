package com.wikigerminare.security;

import java.time.Instant;
import java.util.UUID;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;

import com.wikigerminare.auth.security.AuthJwtProperties;

/**
 * Mints signed test JWTs for the {@link AdminOnly} guard test suite (spec 011-rbac-middleware),
 * following the same {@code JwtEncoder}/{@code JwtClaimsSet} pattern already used by
 * {@code com.wikigerminare.config.SecurityConfigTest}.
 */
public final class AdminOnlyGuardTestSupport {

    private AdminOnlyGuardTestSupport() {
    }

    /** A valid, non-expired token for an authenticated administrator (role=admin, ROLE_ADMIN). */
    public static String adminToken(JwtEncoder jwtEncoder) {
        return signedToken(jwtEncoder, "admin");
    }

    /** A valid, non-expired token for an authenticated non-administrator (role=member, ROLE_MEMBER). */
    public static String memberToken(JwtEncoder jwtEncoder) {
        return signedToken(jwtEncoder, "member");
    }

    /** An expired token, otherwise well-formed for an admin — must be treated as not authenticated. */
    public static String expiredAdminToken(JwtEncoder jwtEncoder) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(AuthJwtProperties.ISSUER)
                .subject(UUID.randomUUID().toString())
                .issuedAt(Instant.now().minusSeconds(1200))
                .expiresAt(Instant.now().minusSeconds(600))
                .claim("role", "admin")
                .build();
        return encode(jwtEncoder, claims);
    }

    private static String signedToken(JwtEncoder jwtEncoder, String role) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(AuthJwtProperties.ISSUER)
                .subject(UUID.randomUUID().toString())
                .issuedAt(Instant.now().minusSeconds(1))
                .expiresAt(Instant.now().plusSeconds(300))
                .claim("role", role)
                .build();
        return encode(jwtEncoder, claims);
    }

    private static String encode(JwtEncoder jwtEncoder, JwtClaimsSet claims) {
        Jwt jwt = jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims));
        return jwt.getTokenValue();
    }

    // Note: the project's JWT decoder only accepts the recognized roles "admin"/"member" (see
    // com.wikigerminare.users.UserRole), so a signed token cannot carry a third, unrecognized role
    // and still pass token validation — such a token is correctly rejected as invalid (401), not
    // delivered as an authenticated-with-no-authority caller. To prove that an authenticated
    // caller holding zero granted authorities is still denied with 403 (never implicitly
    // permitted), AdminOnlyMemberAccessTest builds the Authentication directly instead of through
    // a JWT, via SecurityMockMvcRequestPostProcessors#authentication(Authentication).
}
