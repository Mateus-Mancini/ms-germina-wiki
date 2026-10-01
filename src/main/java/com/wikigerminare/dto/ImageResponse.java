package com.wikigerminare.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * A recorded page image. {@code url} is the stable address to embed in page content.
 */
public record ImageResponse(UUID id, UUID pageId, String fileName, String contentType, long size, UUID uploadedBy,
		Instant createdAt, String url) {
}
