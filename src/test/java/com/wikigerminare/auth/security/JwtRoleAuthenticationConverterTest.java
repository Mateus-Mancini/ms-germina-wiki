package com.wikigerminare.auth.security;

import com.wikigerminare.config.SecurityAuthenticatedUserProvider;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class JwtRoleAuthenticationConverterTest {

    private final JwtRoleAuthenticationConverter converter = new JwtRoleAuthenticationConverter();

    @Test
    void adminReceivesRoleAdminAndCanonicalSubjectName() {
        UUID id = UUID.randomUUID();
        var authentication = converter.convert(jwt(id.toString(), "admin"));

        assertThat(authentication.getName()).isEqualTo(id.toString());
        assertThat(authentication.getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_ADMIN");

        try {
            SecurityContextHolder.getContext().setAuthentication(authentication);
            var currentUser = new SecurityAuthenticatedUserProvider().currentUser();
            assertThat(currentUser.id()).isEqualTo(id);
            assertThat(currentUser.isAdmin()).isTrue();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void memberNeverReceivesAdministrativeAuthority() {
        UUID id = UUID.randomUUID();
        var authentication = converter.convert(jwt(id.toString(), "member"));

        assertThat(authentication.getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_MEMBER")
                .doesNotContain("ROLE_ADMIN");

        try {
            SecurityContextHolder.getContext().setAuthentication(authentication);
            var currentUser = new SecurityAuthenticatedUserProvider().currentUser();
            assertThat(currentUser.id()).isEqualTo(id);
            assertThat(currentUser.isAdmin()).isFalse();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void unknownRoleCannotCreateAuthentication() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> converter.convert(jwt(UUID.randomUUID().toString(), "owner")));
    }

    private static Jwt jwt(String subject, String role) {
        Instant now = Instant.now();
        return new Jwt("token", now, now.plusSeconds(60),
                Map.of("alg", "HS256", "typ", "JWT"),
                Map.of("sub", subject, "role", role));
    }
}
