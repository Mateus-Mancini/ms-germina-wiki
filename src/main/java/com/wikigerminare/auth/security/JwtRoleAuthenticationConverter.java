package com.wikigerminare.auth.security;

import com.wikigerminare.users.UserRole;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.List;

public final class JwtRoleAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        UserRole role;
        try {
            role = UserRole.fromDatabaseValue(jwt.getClaimAsString("role"));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("JWT role is invalid", exception);
        }
        return new JwtAuthenticationToken(
                jwt,
                List.of(new SimpleGrantedAuthority(role.authority())),
                jwt.getSubject());
    }
}
