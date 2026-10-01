package com.wikigerminare.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Confirms an upload so the image is recorded for the page.
 */
public record ConfirmRequest(@NotBlank(message = "uploadKey is required") String uploadKey,
		@NotBlank(message = "fileName is required") @Size(max = 255,
				message = "fileName must have at most 255 characters") String fileName) {
}
