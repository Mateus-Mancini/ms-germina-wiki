package com.wikigerminare.folders.dto;

import java.time.Instant;
import java.util.UUID;

public record FolderResponse(
        UUID id,
        String name,
        UUID parentFolderId,
        UUID createdBy,
        Instant createdAt,
        Instant updatedAt
) {
}