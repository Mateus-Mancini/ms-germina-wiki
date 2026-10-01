package com.wikigerminare.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request for permission to upload one image directly to storage (contracts/images.openapi.yaml).
 */
public record UploadRequest(@NotBlank(message = "contentType is required") String contentType,
		@NotNull(message = "size is required") @Min(value = 1, message = "size must be at least 1 byte")
		@Max(value = 5_242_880, message = "size must be at most 5 MB") Long size) {
}
