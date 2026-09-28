package com.wikigerminare;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.Principal;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;
import com.wikigerminare.config.StorageProperties;
import com.wikigerminare.storage.ObjectStorage;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * The full upload flow against PostgreSQL 18 (Flyway schema) and S3-compatible MinIO: request permission,
 * upload the bytes straight to storage over HTTP, confirm, and check what was recorded.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, StorageTestcontainersConfiguration.class })
class ImageFlowIntegrationTest {

	private static final byte[] PNG = { (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n', 9, 8, 7 };

	private final HttpClient http = HttpClient.newHttpClient();

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private ObjectStorage storage;

	@Autowired
	private S3Client s3;

	@Autowired
	private StorageProperties storageProperties;

	private UUID userId;

	private UUID pageId;

	private Principal principal;

	@BeforeEach
	void seedUserAndPage() {
		userId = UUID.randomUUID();
		pageId = UUID.randomUUID();
		principal = () -> userId.toString();
		jdbc.update("INSERT INTO users (id, name, email, password_hash) VALUES (?, 'Student', ?, 'x')", userId,
				userId + "@example.com");
		jdbc.update("INSERT INTO pages (id, title, slug, created_by) VALUES (?, 'Trip', ?, ?)", pageId,
				"trip-" + pageId, userId);
	}

	@Test
	void uploadGoesStraightToStorageAndIsRecordedAfterConfirmation() throws Exception {
		String permission = requestUpload("image/png", PNG.length);
		String key = JsonPath.read(permission, "$.uploadKey");
		URI uploadUrl = URI.create(JsonPath.read(permission, "$.uploadUrl"));

		assertThat(putBytes(uploadUrl, "image/png", PNG)).isEqualTo(200);

		String image = mvc
			.perform(post("/api/pages/{pageId}/images", pageId).principal(principal)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"uploadKey\":\"" + key + "\",\"fileName\":\"trip.png\"}"))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();

		UUID imageId = UUID.fromString(JsonPath.read(image, "$.id"));
		var row = jdbc.queryForMap("SELECT * FROM page_images WHERE id = ?", imageId);
		assertThat(row).containsEntry("page_id", pageId)
			.containsEntry("file_name", "trip.png")
			.containsEntry("mime_type", "image/png")
			.containsEntry("file_size", (long) PNG.length)
			.containsEntry("uploaded_by", userId);
		String objectKey = (String) row.get("file_url");
		assertThat(objectKey).startsWith("images/");
		assertThat(storage.head(objectKey)).isPresent();
		assertThat(storage.head(key)).as("pending object removed").isEmpty();
	}

	@Test
	void storageRejectsBytesOfAnotherTypeThanPermitted() throws Exception {
		String permission = requestUpload("image/png", PNG.length);

		assertThat(putBytes(URI.create(JsonPath.read(permission, "$.uploadUrl")), "text/html", PNG)).isEqualTo(403);
	}

	@Test
	void confirmingAnObjectThatIsNotAnAllowedImageIsRejectedAndTheObjectRemoved() throws Exception {
		String key = JsonPath.read(requestUpload("image/png", PNG.length), "$.uploadKey");
		// Simulate a stored object that differs from what was allowed (e.g. written by other means).
		s3.putObject(request -> request.bucket(storageProperties.bucket()).key(key).contentType("text/html"),
				RequestBody.fromBytes(PNG));

		mvc.perform(post("/api/pages/{pageId}/images", pageId).principal(principal)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"uploadKey\":\"" + key + "\",\"fileName\":\"x.html\"}")).andExpect(status().isConflict());

		assertThat(storage.head(key)).isEmpty();
		assertThat(jdbc.queryForObject("SELECT count(*) FROM page_images WHERE page_id = ?", Integer.class, pageId))
			.isZero();
	}

	private String requestUpload(String contentType, int size) throws Exception {
		return mvc
			.perform(post("/api/pages/{pageId}/images/uploads", pageId).principal(principal)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"contentType\":\"" + contentType + "\",\"size\":" + size + "}"))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
	}

	private int putBytes(URI url, String contentType, byte[] body) throws Exception {
		return http
			.send(HttpRequest.newBuilder(url)
				.header("Content-Type", contentType)
				.PUT(HttpRequest.BodyPublishers.ofByteArray(body))
				.build(), HttpResponse.BodyHandlers.discarding())
			.statusCode();
	}

}
