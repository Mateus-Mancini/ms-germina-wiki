package com.wikigerminare;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import com.wikigerminare.storage.ObjectStorage;

/**
 * Round trip against the REAL Cloudflare R2 bucket with the exact client the API uses. It proves the
 * R2-specific SDK settings (checksums, chunked encoding) and the bucket CORS rules. Runs only when real
 * storage credentials are in the environment, so it's skipped in CI (see docs/image-storage.md).
 */
// Clear the test-classpath endpoint placeholder so the client uses the real R2 endpoint.
@SpringBootTest(properties = "app.storage.endpoint=")
@Import(TestcontainersConfiguration.class)
@EnabledIfEnvironmentVariable(named = "APP_STORAGE_ACCESS_KEY_ID", matches = ".+")
class R2LiveSmokeTest {

	private static final byte[] PNG = { (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n', 4, 2 };

	private static final String WEB_APP_ORIGIN = "https://germinawiki.web.app";

	private final HttpClient http = HttpClient.newHttpClient();

	@Autowired
	private ObjectStorage storage;

	@Autowired
	private com.wikigerminare.config.StorageProperties properties;

	@Test
	void talksToTheRealR2Endpoint() {
		assertThat(properties.resolvedEndpoint().getHost()).endsWith(".r2.cloudflarestorage.com");
	}

	@Test
	void presignedUploadHeadCopyDownloadAndDeleteAgainstR2() throws Exception {
		String pending = "pending/smoke/" + UUID.randomUUID();
		String stored = "images/smoke-" + UUID.randomUUID();
		try {
			URI putUrl = storage.presignPut(pending, "image/png", PNG.length, Duration.ofMinutes(5));

			HttpResponse<Void> preflight = http.send(HttpRequest.newBuilder(putUrl)
				.method("OPTIONS", HttpRequest.BodyPublishers.noBody())
				.header("Origin", WEB_APP_ORIGIN)
				.header("Access-Control-Request-Method", "PUT")
				.header("Access-Control-Request-Headers", "content-type")
				.build(), HttpResponse.BodyHandlers.discarding());
			assertThat(preflight.headers().firstValue("Access-Control-Allow-Origin"))
				.as("bucket CORS allows the web app").contains(WEB_APP_ORIGIN);

			HttpResponse<Void> put = http.send(HttpRequest.newBuilder(putUrl)
				.header("Content-Type", "image/png")
				.PUT(HttpRequest.BodyPublishers.ofByteArray(PNG))
				.build(), HttpResponse.BodyHandlers.discarding());
			assertThat(put.statusCode()).isEqualTo(200);

			assertThat(storage.head(pending)).contains(new ObjectStorage.ObjectInfo("image/png", PNG.length));
			storage.copy(pending, stored);

			HttpResponse<byte[]> get = http.send(
					HttpRequest.newBuilder(storage.presignGet(stored, Duration.ofMinutes(5))).GET().build(),
					HttpResponse.BodyHandlers.ofByteArray());
			assertThat(get.statusCode()).isEqualTo(200);
			assertThat(get.body()).isEqualTo(PNG);
		}
		finally {
			storage.delete(pending);
			storage.delete(stored);
		}
		assertThat(storage.head(stored)).isEmpty();
	}

}
