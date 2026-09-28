package com.wikigerminare.folders;

import java.time.Instant;
import java.util.UUID;

public interface FolderTreeProjection {

    UUID getId();

    String getName();

    UUID getParentFolderId();

    UUID getCreatedBy();

    Instant getCreatedAt();

    Instant getUpdatedAt();
}