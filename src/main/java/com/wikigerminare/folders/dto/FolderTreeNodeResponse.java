package com.wikigerminare.folders.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record FolderTreeNodeResponse(
        UUID id,
        String name,
        UUID parentFolderId,
        UUID createdBy,
        Instant createdAt,
        Instant updatedAt,
        List<FolderTreeNodeResponse> children
) {
}