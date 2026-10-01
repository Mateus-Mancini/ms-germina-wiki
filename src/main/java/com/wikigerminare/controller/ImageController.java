package com.wikigerminare.controller;

import java.security.Principal;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
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

	@GetMapping("/api/pages/{pageId}/images")
	public List<ImageResponse> list(@PathVariable UUID pageId) {
		return imageService.list(pageId);
	}

	/**
	 * Stable image address for page content: redirects to a short-lived signed storage URL. Public, like the
	 * pages that embed it. The browser may reuse the redirect for 5 minutes, within the URL's 10-minute life.
	 */
	@GetMapping("/api/images/{imageId}")
	public ResponseEntity<Void> image(@PathVariable UUID imageId) {
		return ResponseEntity.status(HttpStatus.FOUND)
			.location(imageService.imageRedirect(imageId))
			.cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePrivate())
			.build();
	}

	@DeleteMapping("/api/images/{imageId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable UUID imageId, Principal principal) {
		imageService.delete(imageId, currentUserId(principal), isAdmin(principal));
	}

	/**
	 * Admin role from the authenticated principal (ROLE_ADMIN, as provided by the auth/RBAC features).
	 */
	static boolean isAdmin(Principal principal) {
		return principal instanceof Authentication authentication && authentication.getAuthorities()
			.stream()
			.anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
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
