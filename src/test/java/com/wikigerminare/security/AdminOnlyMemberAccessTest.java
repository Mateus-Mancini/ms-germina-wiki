package com.wikigerminare.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.web.servlet.MockMvc;

import com.wikigerminare.auth.security.JwtConfiguration;
import com.wikigerminare.config.SecurityConfig;

/**
 * User Story 2 (spec 011-rbac-middleware, P1): an authenticated caller who does not hold
 * {@code ROLE_ADMIN} is always rejected with 403 against an {@link AdminOnly} operation — never
 * implicitly permitted, regardless of which non-admin role (or no role at all) they hold — and
 * the operation's own logic never runs. Unmarked operations remain unaffected.
 */
@WebMvcTest(controllers = AdminOnlyProbeController.class)
@Import({
        SecurityConfig.class,
        JwtConfiguration.class,
        AdminOnlyProbeController.class,
        AdminOnlyMemberAccessTest.WebSecurityTestConfiguration.class
})
class AdminOnlyMemberAccessTest {

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
    void authenticatedMemberIsRejectedWithForbiddenAndNeverExecutesTheOperation() throws Exception {
        mockMvc.perform(get("/security-test/admin-only")
                        .header("Authorization", "Bearer " + AdminOnlyGuardTestSupport.memberToken(jwtEncoder)))
                .andExpect(status().isForbidden());

        assertThat(probeController.adminOnlyCallCount()).isZero();
    }

    @Test
    void anyNonAdminAuthorityIsRejectedWithForbidden() throws Exception {
        Authentication nonAdminRole = new TestingAuthenticationToken(
                "some-other-authenticated-caller", "n/a", List.of(new SimpleGrantedAuthority("ROLE_GUEST")));

        mockMvc.perform(get("/security-test/admin-only").with(authentication(nonAdminRole)))
                .andExpect(status().isForbidden());

        assertThat(probeController.adminOnlyCallCount()).isZero();
    }

    @Test
    void authenticatedCallerWithNoGrantedAuthoritiesIsNeverTreatedAsPermitted() throws Exception {
        Authentication noAuthorities = new TestingAuthenticationToken(
                "authenticated-but-roleless-caller", "n/a", List.of());

        mockMvc.perform(get("/security-test/admin-only").with(authentication(noAuthorities)))
                .andExpect(status().isForbidden());

        assertThat(probeController.adminOnlyCallCount()).isZero();
    }

    @Test
    void memberCanStillReachAnOperationThatIsNotMarkedAdminOnly() throws Exception {
        mockMvc.perform(get("/security-test/unprotected")
                        .header("Authorization", "Bearer " + AdminOnlyGuardTestSupport.memberToken(jwtEncoder)))
                .andExpect(status().isOk());

        assertThat(probeController.unprotectedCallCount()).isEqualTo(1);
    }

    @TestConfiguration
    @EnableWebSecurity
    static class WebSecurityTestConfiguration {
    }
}
