package com.wikigerminare.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import com.wikigerminare.StorageTestcontainersConfiguration;
import com.wikigerminare.TestcontainersConfiguration;

/**
 * The adapter against a real S3-compatible store: presigned URLs work over plain HTTP, and the signed
 * Content-Type and Content-Length are enforced by the store.
 */
@SpringBootTest
@Import({ TestcontainersConfiguration.class, StorageTestcontainersConfiguration.class })
class R2ObjectStorageTest {

	private static final byte[] PNG = { (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n', 1, 2, 3, 4 };

	private final HttpClient http = HttpClient.newHttpClient();

	@Autowired
	private ObjectStorage storage;

	@Test
	void presignedPutThenHeadCopyGetAndDelete() throws Exception {
		String key = "pending/" + UUID.randomUUID();

		assertThat(put(storage.presignPut(key, "image/png", PNG.length, Duration.ofMinutes(10)), "image/png", PNG))
			.isEqualTo(200);
		assertThat(storage.head(key)).contains(new ObjectStorage.ObjectInfo("image/png", PNG.length));

		String finalKey = "images/" + UUID.randomUUID();
		storage.copy(key, finalKey);
		storage.delete(key);
		assertThat(storage.head(key)).isEmpty();

		HttpResponse<byte[]> get = http.send(
				HttpRequest.newBuilder(storage.presignGet(finalKey, Duration.ofMinutes(10))).GET().build(),
				HttpResponse.BodyHandlers.ofByteArray());
		assertThat(get.statusCode()).isEqualTo(200);
		assertThat(get.body()).isEqualTo(PNG);

		storage.delete(finalKey);
		assertThat(storage.head(finalKey)).isEmpty();
	}

	@Test
	void signatureRejectsADifferentContentType() throws Exception {
		String key = "pending/" + UUID.randomUUID();

		int status = put(storage.presignPut(key, "image/png", PNG.length, Duration.ofMinutes(10)), "text/html", PNG);

		assertThat(status).isEqualTo(403);
		assertThat(storage.head(key)).isEmpty();
	}

	@Test
	void signatureRejectsADifferentSize() throws Exception {
		String key = "pending/" + UUID.randomUUID();
		byte[] bigger = new byte[PNG.length + 100];

		int status = put(storage.presignPut(key, "image/png", PNG.length, Duration.ofMinutes(10)), "image/png", bigger);

		assertThat(status).isEqualTo(403);
		assertThat(storage.head(key)).isEmpty();
	}

	@Test
	void deletingAMissingObjectIsNotAnError() {
		storage.delete("images/" + UUID.randomUUID());
	}

	private int put(URI url, String contentType, byte[] body) throws Exception {
		HttpRequest request = HttpRequest.newBuilder(url)
			.header("Content-Type", contentType)
			.PUT(HttpRequest.BodyPublishers.ofByteArray(body))
			.build();
		return http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
	}

}
