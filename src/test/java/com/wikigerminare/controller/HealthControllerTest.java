package com.wikigerminare.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.wikigerminare.dto.ReadinessStatus;
import com.wikigerminare.service.HealthService;

@WebMvcTest(HealthController.class)
class HealthControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private HealthService healthService;

	@Test
	void returns200AndReadyWithoutCredentials() throws Exception {
		when(healthService.checkReadiness()).thenReturn(ReadinessStatus.ready());

		mockMvc.perform(get("/health"))
			.andExpect(status().isOk())
			.andExpect(header().string("Cache-Control", "no-store"))
			.andExpect(content().json("{\"status\":\"ready\"}", true));
	}

	@Test
	void returns503AndNotReadyWithoutInternalDetails() throws Exception {
		when(healthService.checkReadiness()).thenReturn(ReadinessStatus.notReady());

		mockMvc.perform(get("/health"))
			.andExpect(status().isServiceUnavailable())
			.andExpect(header().string("Cache-Control", "no-store"))
			.andExpect(content().json("{\"status\":\"not_ready\"}", true));
	}

}
