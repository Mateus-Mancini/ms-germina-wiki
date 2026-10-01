package com.wikigerminare.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import com.wikigerminare.auth.security.JwtConfiguration;
import com.wikigerminare.config.SecurityConfig;

/**
 * User Story 1 (spec 011-rbac-middleware, P1): an anonymous caller — no identity at all, or an
 * invalid/expired one — is always rejected with 401 against an {@link AdminOnly} operation, and
 * the operation's own logic never runs.
 */
@WebMvcTest(controllers = AdminOnlyProbeController.class)
@Import({
        SecurityConfig.class,
        JwtConfiguration.class,
        AdminOnlyProbeController.class,
        AdminOnlyAnonymousAccessTest.WebSecurityTestConfiguration.class
})
class AdminOnlyAnonymousAccessTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private AdminOnlyProbeController probeController;

    @BeforeEach
    void resetCounters() {
        probeController.reset();
    }

    @Test
    void noAuthorizationHeaderIsRejectedWithUnauthorizedAndNeverExecutesTheOperation() throws Exception {
        mockMvc.perform(get("/security-test/admin-only"))
                .andExpect(status().isUnauthorized());

        assertThat(probeController.adminOnlyCallCount()).isZero();
    }

    @Test
    void invalidOrExpiredTokenIsTreatedAsAnonymousAndNeverExecutesTheOperation() throws Exception {
        mockMvc.perform(get("/security-test/admin-only")
                        .header("Authorization", "******"))
                .andExpect(status().isUnauthorized());
        assertThat(probeController.adminOnlyCallCount()).isZero();

        mockMvc.perform(get("/security-test/admin-only")
                        .header("Authorization", "Bearer " + AdminOnlyGuardTestSupport.expiredAdminToken(jwtEncoder)))
                .andExpect(status().isUnauthorized());
        assertThat(probeController.adminOnlyCallCount()).isZero();
    }

    @TestConfiguration
    @EnableWebSecurity
    static class WebSecurityTestConfiguration {
    }
}
