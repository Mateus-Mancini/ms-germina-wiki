package com.wikigerminare.config;

import com.wikigerminare.integration.AuthenticatedUser;
import com.wikigerminare.integration.AuthenticatedUserProvider;
import com.wikigerminare.service.CommentException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class SecurityAuthenticatedUserProvider implements AuthenticatedUserProvider {
    @Override
    public AuthenticatedUser currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new CommentException("UNAUTHORIZED", "Authentication is required");
        }
        UUID userId;
        try {
            userId = UUID.fromString(authentication.getName());
        } catch (IllegalArgumentException exception) {
            throw new CommentException("UNAUTHORIZED", "Authenticated user id is invalid");
        }
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN")
                        || authority.getAuthority().equals("ADMIN"));
        return new AuthenticatedUser(userId, isAdmin);
    }
}
