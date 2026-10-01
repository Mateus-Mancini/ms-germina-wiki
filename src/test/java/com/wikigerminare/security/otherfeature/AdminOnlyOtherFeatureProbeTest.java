package com.wikigerminare.security.otherfeature;

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
import com.wikigerminare.security.AdminOnlyGuardTestSupport;

/**
 * User Story 4 (spec 011-rbac-middleware, P2): the same 401/403/allow matrix proven for the
 * primary probe in {@code com.wikigerminare.security.*AccessTest} reproduces identically for an
 * operation in a second, independent feature area, using only {@link
 * com.wikigerminare.security.AdminOnly} — no custom permission-checking code.
 */
@WebMvcTest(controllers = AdminOnlyOtherFeatureProbeController.class)
@Import({
        SecurityConfig.class,
        JwtConfiguration.class,
        AdminOnlyOtherFeatureProbeController.class,
        AdminOnlyOtherFeatureProbeTest.WebSecurityTestConfiguration.class
})
class AdminOnlyOtherFeatureProbeTest {

    private static final String URL = "/security-test/other-feature/admin-only";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private AdminOnlyOtherFeatureProbeController probeController;

    @BeforeEach
    void resetCounter() {
        probeController.reset();
    }

    @Test
    void anonymousCallerIsRejectedWithUnauthorized() throws Exception {
        mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
        assertThat(probeController.callCount()).isZero();
    }

    @Test
    void authenticatedMemberIsRejectedWithForbidden() throws Exception {
        mockMvc.perform(get(URL)
                        .header("Authorization", "Bearer " + AdminOnlyGuardTestSupport.memberToken(jwtEncoder)))
                .andExpect(status().isForbidden());
        assertThat(probeController.callCount()).isZero();
    }

    @Test
    void authenticatedAdminIsAllowedAndTheOperationExecutes() throws Exception {
        mockMvc.perform(get(URL)
                        .header("Authorization", "Bearer " + AdminOnlyGuardTestSupport.adminToken(jwtEncoder)))
                .andExpect(status().isOk());
        assertThat(probeController.callCount()).isEqualTo(1);
    }

    @TestConfiguration
    @EnableWebSecurity
    static class WebSecurityTestConfiguration {
    }
}
