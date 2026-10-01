package com.wikigerminare.service;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.wikigerminare.dto.ConfirmRequest;
import com.wikigerminare.dto.ImageResponse;
import com.wikigerminare.dto.UploadPermission;
import com.wikigerminare.dto.UploadRequest;
import com.wikigerminare.repository.ImageRepository;
import com.wikigerminare.repository.ImageRepository.PageImage;
import com.wikigerminare.service.exception.ForbiddenException;
import com.wikigerminare.service.exception.ResourceNotFoundException;
import com.wikigerminare.service.exception.UnauthenticatedException;
import com.wikigerminare.service.exception.UploadMismatchException;
import com.wikigerminare.service.exception.UploadRejectedException;
import com.wikigerminare.storage.ObjectStorage;
import com.wikigerminare.storage.ObjectStorage.ObjectInfo;

/**
 * Page image rules (spec 005): which files may be uploaded, who may confirm them, and how a verified upload
 * becomes a recorded image. Image bytes never pass through the API.
 */
@Service
public class ImageService {

	static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp", "image/gif");

	static final long MAX_SIZE_BYTES = 5L * 1024 * 1024;

	static final Duration UPLOAD_PERMISSION_TTL = Duration.ofMinutes(10);

	static final Duration IMAGE_URL_TTL = Duration.ofMinutes(10);

	private static final Pattern UUID_PATTERN = Pattern
		.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

	private static final Logger log = LoggerFactory.getLogger(ImageService.class);

	private final ImageRepository repository;

	private final ObjectStorage storage;

	private final Clock clock;

	public ImageService(ImageRepository repository, ObjectStorage storage, Clock clock) {
		this.repository = repository;
		this.storage = storage;
		this.clock = clock;
	}

	public UploadPermission requestUpload(UUID pageId, UploadRequest request, UUID userId) {
		requireUser(userId);
		requireAllowed(request.contentType(), request.size());
		requirePage(pageId);

		String key = pendingPrefix(pageId, userId) + UUID.randomUUID();
		var url = storage.presignPut(key, request.contentType(), request.size(), UPLOAD_PERMISSION_TTL);
		return new UploadPermission(key, url.toString(), "PUT", Map.of("Content-Type", request.contentType()),
				clock.instant().plus(UPLOAD_PERMISSION_TTL));
	}

	public ImageResponse confirmUpload(UUID pageId, ConfirmRequest request, UUID userId) {
		requireUser(userId);
		String fileName = request.fileName() == null ? "" : request.fileName().strip();
		if (fileName.isEmpty()) {
			throw new UploadRejectedException("fileName must not be blank");
		}
		requirePage(pageId);

		String pendingKey = request.uploadKey();
		if (!isOwnPendingKey(pendingKey, pageId, userId)) {
			throw new ForbiddenException("uploadKey doesn't belong to this page and user");
		}
		ObjectInfo stored = storage.head(pendingKey)
			.orElseThrow(() -> new ResourceNotFoundException("No uploaded file found for uploadKey"));
		if (!isAllowed(stored.contentType(), stored.contentLength())) {
			storage.delete(pendingKey);
			throw new UploadMismatchException("Uploaded file is not an allowed image type or size");
		}

		String objectKey = "images/" + UUID.randomUUID();
		storage.copy(pendingKey, objectKey);
		storage.delete(pendingKey);
		try {
			return toResponse(repository.insert(pageId, fileName, objectKey, stored.contentType(),
					stored.contentLength(), userId));
		}
		catch (RuntimeException ex) {
			// Don't leave an unrecorded object behind if the row can't be written.
			storage.delete(objectKey);
			throw ex;
		}
	}

	/**
	 * Where the stable address {@code /api/images/{id}} sends the browser: a short-lived signed URL.
	 */
	public URI imageRedirect(UUID imageId) {
		PageImage image = findImage(imageId);
		return storage.presignGet(image.objectKey(), IMAGE_URL_TTL);
	}

	public List<ImageResponse> list(UUID pageId) {
		requirePage(pageId);
		return repository.findByPage(pageId).stream().map(ImageService::toResponse).toList();
	}

	/**
	 * Removes the record and the stored file. The uploader or an admin may delete (FR-007).
	 */
	public void delete(UUID imageId, UUID userId, boolean isAdmin) {
		if (userId == null) {
			throw new UnauthenticatedException("Sign in to delete images");
		}
		PageImage image = findImage(imageId);
		if (!isAdmin && !image.uploadedBy().equals(userId)) {
			throw new ForbiddenException("Only the uploader or an admin can delete this image");
		}
		repository.delete(imageId);
		storage.delete(image.objectKey());
		// The V2 trigger queued the key for the daily cleanup; it's already gone.
		repository.dequeue(image.objectKey());
	}

	private PageImage findImage(UUID imageId) {
		return repository.findById(imageId).orElseThrow(() -> new ResourceNotFoundException("Image not found"));
	}

	static ImageResponse toResponse(PageImage image) {
		return new ImageResponse(image.id(), image.pageId(), image.fileName(), image.contentType(), image.size(),
				image.uploadedBy(), image.createdAt(), "/api/images/" + image.id());
	}

	private void requirePage(UUID pageId) {
		if (!repository.pageExists(pageId)) {
			throw new ResourceNotFoundException("Page not found");
		}
	}

	private static void requireUser(UUID userId) {
		if (userId == null) {
			throw new UnauthenticatedException("Sign in to upload images");
		}
	}

	private static void requireAllowed(String contentType, Long size) {
		if (!ALLOWED_CONTENT_TYPES.contains(contentType)) {
			throw new UploadRejectedException("contentType must be one of image/jpeg, image/png, image/webp, image/gif");
		}
		if (size == null || size < 1 || size > MAX_SIZE_BYTES) {
			throw new UploadRejectedException("size must be between 1 byte and 5 MB");
		}
	}

	private static boolean isAllowed(String contentType, long size) {
		return ALLOWED_CONTENT_TYPES.contains(contentType) && size >= 1 && size <= MAX_SIZE_BYTES;
	}

	private static String pendingPrefix(UUID pageId, UUID userId) {
		return "pending/" + pageId + "/" + userId + "/";
	}

	private static boolean isOwnPendingKey(String key, UUID pageId, UUID userId) {
		String prefix = pendingPrefix(pageId, userId);
		boolean own = key != null && key.startsWith(prefix) && UUID_PATTERN.matcher(key.substring(prefix.length())).matches();
		if (!own) {
			log.debug("Rejected upload key for page {}", pageId);
		}
		return own;
	}

}
