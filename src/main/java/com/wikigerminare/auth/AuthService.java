package com.wikigerminare.auth;

import com.wikigerminare.auth.dto.LoginRequest;
import com.wikigerminare.auth.dto.LoginResponse;
import com.wikigerminare.auth.security.AuthenticatedUserDetails;
import com.wikigerminare.auth.security.JwtTokenService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenService jwtTokenService;

    public AuthService(AuthenticationManager authenticationManager, JwtTokenService jwtTokenService) {
        this.authenticationManager = authenticationManager;
        this.jwtTokenService = jwtTokenService;
    }

    public LoginResponse login(LoginRequest request) {
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(request.getEmail(), request.getPassword()));
        } catch (BadCredentialsException exception) {
            throw new InvalidCredentialsException();
        }
        if (!(authentication.getPrincipal() instanceof AuthenticatedUserDetails user)) {
            throw new IllegalStateException("Authentication returned an unsupported principal");
        }
        JwtTokenService.IssuedToken issued = jwtTokenService.issue(user.id(), user.role());
        return new LoginResponse(issued.accessToken(), issued.expiresAt());
    }
}
