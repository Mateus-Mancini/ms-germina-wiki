package com.wikigerminare.pages.dto;

import java.time.Instant;
import java.util.UUID;

public record PageResponse(
        UUID id,
        String title,
        String slug,
        String content,
        Integer version,
        UUID folderId,
        UUID createdBy,
        UUID updatedBy,
        Instant createdAt,
        Instant updatedAt
) {
}