package com.wikigerminare.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.core.convert.converter.Converter;

/**
 * {@code @EnableMethodSecurity} activates declarative {@code @PreAuthorize}-based authorization
 * (spec 011-rbac-middleware), so that {@code com.wikigerminare.security.AdminOnly} and any other
 * method-security annotation are enforced project-wide. It does not alter the filter chain, the
 * public route matchers, or the 401 authentication entry point configured below.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter) throws Exception {
        return http
            .csrf(csrf -> csrf.disable())
            // Stateless token API: no HTTP session and no saved-request cache. The Lambda adapter has no
            // session for Function URL (HTTP API v2) events, so touching it crashes the request (502).
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .requestCache(cache -> cache.disable())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/health",
                    "/__snapstart-priming",
                    "/v3/api-docs/**",
                    "/swagger-ui/**",
                    "/swagger-ui.html"
                ).permitAll()
                .requestMatchers(HttpMethod.POST, "/api/auth/login", "/api/auth/register").permitAll()
                // Image addresses are embedded in pages and loaded by <img> tags, which send no credentials
                // (spec 005, FR-006); they redirect to short-lived signed URLs.
                .requestMatchers(HttpMethod.GET, "/api/images/*").permitAll()
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt ->
                jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)))
            // REST semantics: a missing or invalid identity is 401, not Spring's default 403.
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
            .build();
    }
}
