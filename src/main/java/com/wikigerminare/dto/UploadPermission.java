package com.wikigerminare.dto;

import java.time.Instant;
import java.util.Map;

/**
 * A presigned PUT: the browser must send exactly these headers and the declared number of bytes.
 */
public record UploadPermission(String uploadKey, String uploadUrl, String method, Map<String, String> headers,
		Instant expiresAt) {
}
