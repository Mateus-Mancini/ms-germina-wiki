package com.wikigerminare.pages;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;
import com.wikigerminare.TestcontainersConfiguration;

/**
 * Page persistence against real PostgreSQL (the service tests mock the repository). Regression for
 * production: creating a page returned 502 because the service assigned an id to a @GeneratedValue entity
 * that also has a version, so Spring Data merged it as an existing row and Hibernate threw an
 * optimistic-locking failure.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PagePersistenceIntegrationTest {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Test
	void createsAPageWithVersionOneAndEnforcesOptimisticLocking() throws Exception {
		UUID user = UUID.randomUUID();
		UUID folder = UUID.randomUUID();
		jdbc.update("INSERT INTO users (id, name, email, password_hash) VALUES (?, 'Author', ?, 'x')", user,
				user + "@example.com");
		jdbc.update("INSERT INTO folders (id, name, created_by) VALUES (?, 'Folder', ?)", folder, user);

		var created = mvc.perform(as(user, post("/api/pages")).contentType(MediaType.APPLICATION_JSON)
			.content("{\"title\":\"Trip\",\"slug\":\"trip-" + user + "\",\"content\":\"Hello [[World]]\",\"folderId\":\""
					+ folder + "\"}"))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse();
		UUID pageId = UUID.fromString(JsonPath.read(created.getContentAsString(), "$.id"));
		assertThat(jdbc.queryForObject("SELECT version FROM pages WHERE id = ?", Integer.class, pageId)).isEqualTo(1);

		// The editor saves a freshly created page with the ETag from the creation response, without a reload.
		String etag = created.getHeader("ETag");
		assertThat(etag).isNotBlank();
		mvc.perform(as(user, get("/api/pages/{id}", pageId)))
			.andExpect(status().isOk())
			.andExpect(header().string("ETag", etag));

		mvc.perform(as(user, patch("/api/pages/{id}", pageId)).header("If-Match", etag)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"content\":\"Edited\"}")).andExpect(status().isOk());
		assertThat(jdbc.queryForObject("SELECT version FROM pages WHERE id = ?", Integer.class, pageId)).isEqualTo(2);

		// The old ETag is now stale.
		mvc.perform(as(user, patch("/api/pages/{id}", pageId)).header("If-Match", etag)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"content\":\"Stale edit\"}")).andExpect(status().is(org.hamcrest.Matchers.oneOf(409, 412)));
	}

	private static MockHttpServletRequestBuilder as(UUID user, MockHttpServletRequestBuilder request) {
		return request.with(jwt().jwt(token -> token.subject(user.toString()).claim("role", "admin")).authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN")));
	}

}
