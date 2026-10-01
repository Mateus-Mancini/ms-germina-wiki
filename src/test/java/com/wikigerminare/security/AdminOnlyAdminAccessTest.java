package com.wikigerminare.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.web.servlet.MockMvc;

import com.wikigerminare.auth.security.JwtConfiguration;
import com.wikigerminare.config.SecurityConfig;

/**
 * User Story 3 (spec 011-rbac-middleware, P1): an authenticated administrator is always allowed
 * through an {@link AdminOnly} operation, exactly as if the guard were not present.
 */
@WebMvcTest(controllers = AdminOnlyProbeController.class)
@Import({
        SecurityConfig.class,
        JwtConfiguration.class,
        AdminOnlyProbeController.class,
        AdminOnlyAdminAccessTest.WebSecurityTestConfiguration.class
})
class AdminOnlyAdminAccessTest {

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
    void authenticatedAdminIsAllowedAndTheOperationExecutesExactlyOnce() throws Exception {
        mockMvc.perform(get("/security-test/admin-only")
                        .header("Authorization", "Bearer " + AdminOnlyGuardTestSupport.adminToken(jwtEncoder)))
                .andExpect(status().isOk());

        assertThat(probeController.adminOnlyCallCount()).isEqualTo(1);
    }

    @Test
    void consecutiveAdminRequestsEachExecuteTheOperationAndAdminCanReachUnprotectedOperationsToo() throws Exception {
        String adminAuthorizationHeader = "Bearer " + AdminOnlyGuardTestSupport.adminToken(jwtEncoder);

        mockMvc.perform(get("/security-test/admin-only").header("Authorization", adminAuthorizationHeader))
                .andExpect(status().isOk());
        mockMvc.perform(get("/security-test/admin-only").header("Authorization", adminAuthorizationHeader))
                .andExpect(status().isOk());

        assertThat(probeController.adminOnlyCallCount()).isEqualTo(2);

        mockMvc.perform(get("/security-test/unprotected").header("Authorization", adminAuthorizationHeader))
                .andExpect(status().isOk());

        assertThat(probeController.unprotectedCallCount()).isEqualTo(1);
    }

    @TestConfiguration
    @EnableWebSecurity
    static class WebSecurityTestConfiguration {
    }
}
