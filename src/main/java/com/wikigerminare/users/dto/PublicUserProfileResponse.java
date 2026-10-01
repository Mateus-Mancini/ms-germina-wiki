package com.wikigerminare.users.dto;

import java.util.UUID;

public record PublicUserProfileResponse(
        UUID id,
        String name,
        String avatarUrl,
        String bio
) {
}
