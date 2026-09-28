package com.wikigerminare.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.security.Principal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.wikigerminare.dto.ConfirmRequest;
import com.wikigerminare.dto.ImageResponse;
import com.wikigerminare.dto.UploadPermission;
import com.wikigerminare.dto.UploadRequest;
import com.wikigerminare.service.ImageService;
import com.wikigerminare.service.exception.ForbiddenException;
import com.wikigerminare.service.exception.ResourceNotFoundException;
import com.wikigerminare.service.exception.UnauthenticatedException;
import com.wikigerminare.service.exception.UploadMismatchException;
import com.wikigerminare.service.exception.UploadRejectedException;

@WebMvcTest(ImageController.class)
class ImageControllerTest {

	private final UUID pageId = UUID.randomUUID();

	private final UUID userId = UUID.randomUUID();

	private final Principal principal = () -> userId.toString();

	@Autowired
	private MockMvc mvc;

	@MockitoBean
	private ImageService imageService;

	@Test
	void requestUploadReturns201WithThePermission() throws Exception {
		when(imageService.requestUpload(eq(pageId), eq(new UploadRequest("image/png", 10L)), eq(userId)))
			.thenReturn(new UploadPermission("pending/k", "https://r2/put", "PUT", Map.of("Content-Type", "image/png"),
					Instant.parse("2026-09-28T12:10:00Z")));

		mvc.perform(json(post("/api/pages/{pageId}/images/uploads", pageId), "{\"contentType\":\"image/png\",\"size\":10}")
			.principal(principal))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.uploadKey").value("pending/k"))
			.andExpect(jsonPath("$.uploadUrl").value("https://r2/put"))
			.andExpect(jsonPath("$.method").value("PUT"))
			.andExpect(jsonPath("$.headers['Content-Type']").value("image/png"))
			.andExpect(jsonPath("$.expiresAt").value("2026-09-28T12:10:00Z"));
	}

	@Test
	void requestUploadValidatesTheBody() throws Exception {
		mvc.perform(json(post("/api/pages/{pageId}/images/uploads", pageId), "{\"contentType\":\"image/png\",\"size\":0}")
			.principal(principal)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").exists());
		mvc.perform(json(post("/api/pages/{pageId}/images/uploads", pageId), "{\"size\":10}").principal(principal))
			.andExpect(status().isBadRequest());
		mvc.perform(json(post("/api/pages/{pageId}/images/uploads", pageId), "not json").principal(principal))
			.andExpect(status().isBadRequest());
	}

	@Test
	void mapsServiceErrorsToStatusCodesWithAnErrorBody() throws Exception {
		assertError(new UnauthenticatedException("sign in"), 401);
		assertError(new UploadRejectedException("bad type"), 400);
		assertError(new ResourceNotFoundException("no page"), 404);
	}

	@Test
	void anonymousRequestsReachTheServiceWithoutAUser() throws Exception {
		when(imageService.requestUpload(any(), any(), eq(null))).thenThrow(new UnauthenticatedException("sign in"));

		mvc.perform(json(post("/api/pages/{pageId}/images/uploads", pageId), "{\"contentType\":\"image/png\",\"size\":10}"))
			.andExpect(status().isUnauthorized())
			.andExpect(content().json("{\"error\":\"sign in\"}", true));
	}

	@Test
	void aPrincipalThatIsNotAUuidIsUnauthenticated() throws Exception {
		mvc.perform(json(post("/api/pages/{pageId}/images/uploads", pageId), "{\"contentType\":\"image/png\",\"size\":10}")
			.principal(() -> "not-a-uuid")).andExpect(status().isUnauthorized());
	}

	@Test
	void confirmReturns201WithTheImage() throws Exception {
		UUID imageId = UUID.randomUUID();
		when(imageService.confirmUpload(eq(pageId), eq(new ConfirmRequest("pending/k", "a.png")), eq(userId)))
			.thenReturn(new ImageResponse(imageId, pageId, "a.png", "image/png", 10, userId,
					Instant.parse("2026-09-28T12:00:00Z"), "/api/images/" + imageId));

		mvc.perform(json(post("/api/pages/{pageId}/images", pageId), "{\"uploadKey\":\"pending/k\",\"fileName\":\"a.png\"}")
			.principal(principal))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.id").value(imageId.toString()))
			.andExpect(jsonPath("$.url").value("/api/images/" + imageId))
			.andExpect(jsonPath("$.size").value(10));
	}

	@Test
	void confirmMapsForbiddenAndMismatch() throws Exception {
		when(imageService.confirmUpload(any(), eq(new ConfirmRequest("pending/other", "a.png")), any()))
			.thenThrow(new ForbiddenException("not yours"));
		when(imageService.confirmUpload(any(), eq(new ConfirmRequest("pending/bad", "a.png")), any()))
			.thenThrow(new UploadMismatchException("type differs"));

		mvc.perform(json(post("/api/pages/{pageId}/images", pageId), "{\"uploadKey\":\"pending/other\",\"fileName\":\"a.png\"}")
			.principal(principal)).andExpect(status().isForbidden());
		mvc.perform(json(post("/api/pages/{pageId}/images", pageId), "{\"uploadKey\":\"pending/bad\",\"fileName\":\"a.png\"}")
			.principal(principal)).andExpect(status().isConflict());
		mvc.perform(json(post("/api/pages/{pageId}/images", pageId), "{\"uploadKey\":\"pending/k\",\"fileName\":\"\"}")
			.principal(principal)).andExpect(status().isBadRequest());
	}

	private void assertError(RuntimeException error, int status) throws Exception {
		doThrow(error).when(imageService).requestUpload(any(), any(), any());
		mvc.perform(json(post("/api/pages/{pageId}/images/uploads", pageId), "{\"contentType\":\"image/png\",\"size\":10}")
			.principal(principal))
			.andExpect(status().is(status))
			.andExpect(content().json("{\"error\":\"" + error.getMessage() + "\"}", true));
	}

	private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder builder, String body) {
		return builder.contentType(MediaType.APPLICATION_JSON).content(body);
	}

}
