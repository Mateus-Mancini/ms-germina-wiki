package com.wikigerminare.auth.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.proc.SecurityContext;
import com.wikigerminare.users.UserRole;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.SecretKey;
import java.util.UUID;

@Configuration
@EnableConfigurationProperties(AuthJwtProperties.class)
public class JwtConfiguration {

    @Bean
    SecretKey authJwtSecretKey(AuthJwtProperties properties) {
        return properties.secretKey();
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey authJwtSecretKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(authJwtSecretKey.getEncoded()));
    }

    @Bean
    public JwtDecoder jwtDecoder(SecretKey authJwtSecretKey) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(authJwtSecretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        decoder.setJwtValidator(new org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(AuthJwtProperties.ISSUER),
                requiredClaimsValidator()));
        return decoder;
    }

    @Bean
    Converter<Jwt, org.springframework.security.authentication.AbstractAuthenticationToken> jwtAuthenticationConverter() {
        return new JwtRoleAuthenticationConverter();
    }

    private static org.springframework.security.oauth2.core.OAuth2TokenValidator<Jwt> requiredClaimsValidator() {
        return jwt -> {
            String subject = jwt.getSubject();
            String role = jwt.getClaimAsString("role");
            boolean validSubject = false;
            if (subject != null) {
                try {
                    validSubject = UUID.fromString(subject).toString().equals(subject);
                } catch (IllegalArgumentException exception) {
                    return invalidClaims();
                }
            }
            try {
                UserRole.fromDatabaseValue(role);
            } catch (IllegalArgumentException exception) {
                return invalidClaims();
            }
            if (validSubject && jwt.getClaims().containsKey("iat") && jwt.getIssuedAt() != null
                    && jwt.getClaims().containsKey("exp") && jwt.getExpiresAt() != null) {
                return org.springframework.security.oauth2.core.OAuth2TokenValidatorResult.success();
            }
            return invalidClaims();
        };
    }

    private static org.springframework.security.oauth2.core.OAuth2TokenValidatorResult invalidClaims() {
        return org.springframework.security.oauth2.core.OAuth2TokenValidatorResult.failure(
                new org.springframework.security.oauth2.core.OAuth2Error(
                        "invalid_token", "Required token claims are invalid", null));
    }
}
