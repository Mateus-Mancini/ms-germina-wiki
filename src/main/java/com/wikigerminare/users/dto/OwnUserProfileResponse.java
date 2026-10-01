package com.wikigerminare.users.dto;

import java.util.UUID;

public record OwnUserProfileResponse(
        UUID id,
        String name,
        String email,
        String avatarUrl,
        String bio
) {
}
