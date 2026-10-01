package com.wikigerminare.auth.dto;

import java.time.Instant;

public record LoginResponse(String accessToken, String tokenType, Instant expiresAt) {

    public LoginResponse(String accessToken, Instant expiresAt) {
        this(accessToken, "Bearer", expiresAt);
    }
}
