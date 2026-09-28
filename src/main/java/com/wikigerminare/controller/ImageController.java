package com.wikigerminare.controller;

import java.security.Principal;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.wikigerminare.dto.ConfirmRequest;
import com.wikigerminare.dto.ImageResponse;
import com.wikigerminare.dto.UploadPermission;
import com.wikigerminare.dto.UploadRequest;
import com.wikigerminare.service.ImageService;
import com.wikigerminare.service.exception.UnauthenticatedException;

import jakarta.validation.Valid;

/**
 * Page image endpoints (specs/005-image-storage/contracts/images.openapi.yaml). HTTP only; all rules live in
 * {@link ImageService}.
 */
@RestController
public class ImageController {

	private final ImageService imageService;

	public ImageController(ImageService imageService) {
		this.imageService = imageService;
	}

	@PostMapping("/api/pages/{pageId}/images/uploads")
	@ResponseStatus(HttpStatus.CREATED)
	public UploadPermission requestUpload(@PathVariable UUID pageId, @Valid @RequestBody UploadRequest request,
			Principal principal) {
		return imageService.requestUpload(pageId, request, currentUserId(principal));
	}

	@PostMapping("/api/pages/{pageId}/images")
	@ResponseStatus(HttpStatus.CREATED)
	public ImageResponse confirmUpload(@PathVariable UUID pageId, @Valid @RequestBody ConfirmRequest request,
			Principal principal) {
		return imageService.confirmUpload(pageId, request, currentUserId(principal));
	}

	/**
	 * The signed-in user's id (auth-api sets the principal name to the user UUID), or null when anonymous.
	 */
	static UUID currentUserId(Principal principal) {
		if (principal == null) {
			return null;
		}
		try {
			return UUID.fromString(principal.getName());
		}
		catch (IllegalArgumentException ex) {
			throw new UnauthenticatedException("Invalid user identity");
		}
	}

}
