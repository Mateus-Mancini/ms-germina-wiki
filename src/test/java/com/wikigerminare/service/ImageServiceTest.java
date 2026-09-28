package com.wikigerminare.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.wikigerminare.dto.ConfirmRequest;
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

class ImageServiceTest {

	private static final Instant NOW = Instant.parse("2026-09-28T12:00:00Z");

	private final UUID pageId = UUID.randomUUID();

	private final UUID userId = UUID.randomUUID();

	private ImageRepository repository;

	private FakeStorage storage;

	private ImageService service;

	@BeforeEach
	void setUp() {
		repository = mock(ImageRepository.class);
		storage = new FakeStorage();
		service = new ImageService(repository, storage, Clock.fixed(NOW, ZoneOffset.UTC));
		when(repository.pageExists(pageId)).thenReturn(true);
	}

	@Nested
	class RequestUpload {

		@Test
		void returnsAPresignedPutForAPendingKeyBoundToPageAndUser() {
			UploadPermission permission = service.requestUpload(pageId, new UploadRequest("image/png", 1234L), userId);

			assertThat(permission.uploadKey()).matches("pending/" + pageId + "/" + userId + "/[0-9a-f-]{36}");
			assertThat(permission.method()).isEqualTo("PUT");
			assertThat(permission.headers()).containsExactly(Map.entry("Content-Type", "image/png"));
			assertThat(permission.expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(10)));
			assertThat(storage.lastPresignedPut).isEqualTo(permission.uploadKey() + "|image/png|1234|PT10M");
		}

		@Test
		void rejectsDisallowedTypes() {
			assertThatThrownBy(() -> service.requestUpload(pageId, new UploadRequest("image/svg+xml", 10L), userId))
				.isInstanceOf(UploadRejectedException.class);
			assertThat(storage.lastPresignedPut).isNull();
		}

		@Test
		void rejectsSizesOutsideOneByteToFiveMegabytes() {
			assertThatThrownBy(() -> service.requestUpload(pageId, new UploadRequest("image/png", 0L), userId))
				.isInstanceOf(UploadRejectedException.class);
			assertThatThrownBy(() -> service.requestUpload(pageId, new UploadRequest("image/png", 5_242_881L), userId))
				.isInstanceOf(UploadRejectedException.class);
		}

		@Test
		void requiresASignedInUser() {
			assertThatThrownBy(() -> service.requestUpload(pageId, new UploadRequest("image/png", 10L), null))
				.isInstanceOf(UnauthenticatedException.class);
		}

		@Test
		void requiresAnExistingPage() {
			UUID missing = UUID.randomUUID();
			assertThatThrownBy(() -> service.requestUpload(missing, new UploadRequest("image/png", 10L), userId))
				.isInstanceOf(ResourceNotFoundException.class);
		}

	}

	@Nested
	class ConfirmUpload {

		private String pendingKey;

		@BeforeEach
		void uploaded() {
			pendingKey = "pending/" + pageId + "/" + userId + "/" + UUID.randomUUID();
			storage.objects.put(pendingKey, new ObjectStorage.ObjectInfo("image/png", 1234));
			when(repository.insert(eq(pageId), anyString(), anyString(), anyString(), anyLong(), eq(userId)))
				.thenAnswer(call -> new PageImage(UUID.randomUUID(), pageId, call.getArgument(1), call.getArgument(2),
						call.getArgument(3), call.getArgument(4), userId, NOW));
		}

		@Test
		void movesTheObjectToImagesAndRecordsIt() {
			var image = service.confirmUpload(pageId, new ConfirmRequest(pendingKey, "  trip.png  "), userId);

			assertThat(storage.objects).doesNotContainKey(pendingKey);
			String finalKey = storage.objects.keySet().iterator().next();
			assertThat(finalKey).matches("images/[0-9a-f-]{36}");
			verify(repository).insert(pageId, "trip.png", finalKey, "image/png", 1234L, userId);
			assertThat(image.url()).isEqualTo("/api/images/" + image.id());
			assertThat(image.fileName()).isEqualTo("trip.png");
		}

		@Test
		void refusesAKeyOfAnotherUserOrPage() {
			String foreign = "pending/" + pageId + "/" + UUID.randomUUID() + "/" + UUID.randomUUID();
			storage.objects.put(foreign, new ObjectStorage.ObjectInfo("image/png", 10));

			assertThatThrownBy(() -> service.confirmUpload(pageId, new ConfirmRequest(foreign, "x.png"), userId))
				.isInstanceOf(ForbiddenException.class);
			assertThatThrownBy(
					() -> service.confirmUpload(pageId, new ConfirmRequest("images/" + UUID.randomUUID(), "x.png"), userId))
				.isInstanceOf(ForbiddenException.class);
			assertThat(storage.objects).containsKey(foreign);
		}

		@Test
		void reportsAMissingUploadAsNotFound() {
			String neverUploaded = "pending/" + pageId + "/" + userId + "/" + UUID.randomUUID();

			assertThatThrownBy(() -> service.confirmUpload(pageId, new ConfirmRequest(neverUploaded, "x.png"), userId))
				.isInstanceOf(ResourceNotFoundException.class);
		}

		@Test
		void rejectsAndDeletesAnObjectThatDoesNotMatchTheAllowedTypesOrSizes() {
			storage.objects.put(pendingKey, new ObjectStorage.ObjectInfo("text/html", 1234));

			assertThatThrownBy(() -> service.confirmUpload(pageId, new ConfirmRequest(pendingKey, "x.png"), userId))
				.isInstanceOf(UploadMismatchException.class);
			assertThat(storage.objects).doesNotContainKey(pendingKey);
			verify(repository, never()).insert(any(), any(), any(), any(), anyLong(), any());
		}

		@Test
		void requiresAFileNameThatIsNotBlank() {
			assertThatThrownBy(() -> service.confirmUpload(pageId, new ConfirmRequest(pendingKey, "   "), userId))
				.isInstanceOf(UploadRejectedException.class);
		}

		@Test
		void requiresASignedInUserAndAnExistingPage() {
			assertThatThrownBy(() -> service.confirmUpload(pageId, new ConfirmRequest(pendingKey, "x.png"), null))
				.isInstanceOf(UnauthenticatedException.class);
			assertThatThrownBy(
					() -> service.confirmUpload(UUID.randomUUID(), new ConfirmRequest(pendingKey, "x.png"), userId))
				.isInstanceOf(ResourceNotFoundException.class);
		}

	}

	@Nested
	class ReadAndManage {

		private final UUID imageId = UUID.randomUUID();

		private final PageImage image = new PageImage(imageId, pageId, "a.png", "images/" + imageId, "image/png", 10,
				userId, NOW);

		@BeforeEach
		void recorded() {
			when(repository.findById(imageId)).thenReturn(Optional.of(image));
			storage.objects.put(image.objectKey(), new ObjectStorage.ObjectInfo("image/png", 10));
		}

		@Test
		void redirectsToAPresignedGetValidForTenMinutes() {
			assertThat(service.imageRedirect(imageId).toString())
				.isEqualTo("https://storage.example/images/" + imageId + "?signed-get&ttl=PT10M");
		}

		@Test
		void unknownImagesAreNotFound() {
			UUID unknown = UUID.randomUUID();
			when(repository.findById(unknown)).thenReturn(Optional.empty());

			assertThatThrownBy(() -> service.imageRedirect(unknown)).isInstanceOf(ResourceNotFoundException.class);
			assertThatThrownBy(() -> service.delete(unknown, userId)).isInstanceOf(ResourceNotFoundException.class);
		}

		@Test
		void listsAPagesImagesAndRequiresThePage() {
			when(repository.findByPage(pageId)).thenReturn(java.util.List.of(image));

			assertThat(service.list(pageId)).extracting("id").containsExactly(imageId);
			assertThatThrownBy(() -> service.list(UUID.randomUUID())).isInstanceOf(ResourceNotFoundException.class);
		}

		@Test
		void theUploaderDeletesTheRecordAndTheStoredFile() {
			service.delete(imageId, userId);

			verify(repository).delete(imageId);
			verify(repository).dequeue(image.objectKey());
			assertThat(storage.objects).doesNotContainKey(image.objectKey());
		}

		@Test
		void otherUsersMayNotDeleteAndAnonymousUsersMustSignIn() {
			assertThatThrownBy(() -> service.delete(imageId, UUID.randomUUID())).isInstanceOf(ForbiddenException.class);
			assertThatThrownBy(() -> service.delete(imageId, null)).isInstanceOf(UnauthenticatedException.class);
			verify(repository, never()).delete(any());
			assertThat(storage.objects).containsKey(image.objectKey());
		}

	}

	/**
	 * In-memory ObjectStorage.
	 */
	static class FakeStorage implements ObjectStorage {

		final Map<String, ObjectInfo> objects = new HashMap<>();

		String lastPresignedPut;

		@Override
		public URI presignPut(String key, String contentType, long contentLength, Duration ttl) {
			lastPresignedPut = key + "|" + contentType + "|" + contentLength + "|" + ttl;
			return URI.create("https://storage.example/" + key + "?signed-put");
		}

		@Override
		public URI presignGet(String key, Duration ttl) {
			return URI.create("https://storage.example/" + key + "?signed-get&ttl=" + ttl);
		}

		@Override
		public Optional<ObjectInfo> head(String key) {
			return Optional.ofNullable(objects.get(key));
		}

		@Override
		public void copy(String sourceKey, String targetKey) {
			objects.put(targetKey, objects.get(sourceKey));
		}

		@Override
		public void delete(String key) {
			objects.remove(key);
		}

	}

}
