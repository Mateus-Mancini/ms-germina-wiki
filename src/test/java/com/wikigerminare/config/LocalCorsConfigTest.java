package com.wikigerminare.config;

import static org.hamcrest.Matchers.containsStringIgnoringCase;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(controllers = LocalCorsConfigTest.PingController.class)
@Import({ LocalCorsConfig.class, LocalCorsConfigTest.PingController.class })
@ActiveProfiles("local")
class LocalCorsConfigTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void allowsPreflightFromLocalFrontend() throws Exception {
		mockMvc.perform(options("/ping")
				.header("Origin", "http://localhost:3000")
				.header("Access-Control-Request-Method", "GET"))
			.andExpect(status().isOk())
			.andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"));
	}

	@Test
	void allowsTheEditorsConditionalSaveAndExposesTheEtag() throws Exception {
		mockMvc.perform(options("/ping")
				.header("Origin", "http://localhost:3000")
				.header("Access-Control-Request-Method", "PATCH")
				.header("Access-Control-Request-Headers", "authorization,content-type,if-match"))
			.andExpect(status().isOk())
			.andExpect(header().string("Access-Control-Allow-Headers", containsStringIgnoringCase("if-match")));
		mockMvc.perform(get("/ping").header("Origin", "http://localhost:3000"))
			.andExpect(status().isOk())
			.andExpect(header().string("Access-Control-Expose-Headers", containsStringIgnoringCase("etag")));
	}

	@Test
	void rejectsPreflightFromUnknownOrigin() throws Exception {
		mockMvc.perform(options("/ping")
				.header("Origin", "https://evil.example")
				.header("Access-Control-Request-Method", "GET"))
			.andExpect(status().isForbidden())
			.andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
	}

	@RestController
	static class PingController {

		@GetMapping("/ping")
		String ping() {
			return "pong";
		}

	}

}
