package com.wikigerminare.auth;

import com.wikigerminare.auth.dto.LoginRequest;
import com.wikigerminare.auth.dto.LoginResponse;
import com.wikigerminare.auth.security.AuthenticatedUserDetails;
import com.wikigerminare.auth.security.JwtTokenService;
import com.wikigerminare.users.User;
import com.wikigerminare.users.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AuthServiceTest {

    private AuthenticationManager authenticationManager;
    private JwtTokenService jwtTokenService;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        authenticationManager = mock(AuthenticationManager.class);
        jwtTokenService = mock(JwtTokenService.class);
        authService = new AuthService(authenticationManager, jwtTokenService);
    }

    @Test
    void validCredentialsIssueTokenForPersistedUser() {
        UUID userId = UUID.randomUUID();
        AuthenticatedUserDetails details = authenticatedDetails(userId, UserRole.ADMIN);
        when(authenticationManager.authenticate(org.mockito.ArgumentMatchers.any()))
                .thenReturn(UsernamePasswordAuthenticationToken.authenticated(
                        details, null, details.getAuthorities()));
        Instant expiresAt = Instant.parse("2026-10-01T10:00:00Z");
        when(jwtTokenService.issue(userId, UserRole.ADMIN))
                .thenReturn(new JwtTokenService.IssuedToken("signed-token", expiresAt));

        LoginRequest request = request("student@example.com", "correct-password");
        LoginResponse response = authService.login(request);

        assertThat(response.accessToken()).isEqualTo("signed-token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresAt()).isEqualTo(expiresAt);
        verify(jwtTokenService).issue(userId, UserRole.ADMIN);
    }

    @Test
    void unknownEmailAndWrongPasswordHaveSamePublicFailure() {
        LoginRequest request = request("missing@example.com", "wrong-password");
        when(authenticationManager.authenticate(org.mockito.ArgumentMatchers.any()))
                .thenThrow(new BadCredentialsException("provider details must not escape"));

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid email or password");
        verifyNoInteractions(jwtTokenService);
    }

    private static AuthenticatedUserDetails authenticatedDetails(UUID userId, UserRole role) {
        User user = mock(User.class);
        when(user.getId()).thenReturn(userId);
        when(user.getEmail()).thenReturn("student@example.com");
        when(user.getPasswordHash()).thenReturn("$2a$test-hash");
        when(user.getRole()).thenReturn(role);
        return new AuthenticatedUserDetails(user);
    }

    private static LoginRequest request(String email, String password) {
        LoginRequest request = new LoginRequest();
        request.setEmail(email);
        request.setPassword(password);
        return request;
    }
}
