package com.wikigerminare.auth.security;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

@Validated
@ConfigurationProperties(prefix = "app.auth.jwt")
public record AuthJwtProperties(
        @NotBlank String secretBase64,
        @Min(1) @Max(3600) long ttlSeconds) {

    public static final String ISSUER = "germinawiki";

    public SecretKey secretKey() {
        byte[] key;
        try {
            key = Base64.getDecoder().decode(secretBase64);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("APP_AUTH_JWT_SECRET_BASE64 must be valid Base64", exception);
        }
        if (key.length < 32) {
            throw new IllegalArgumentException("APP_AUTH_JWT_SECRET_BASE64 must decode to at least 32 bytes");
        }
        return new SecretKeySpec(key, "HmacSHA256");
    }
}
