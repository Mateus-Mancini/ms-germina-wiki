package com.wikigerminare;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Readiness against a real PostgreSQL. Uses its own container (not the shared one) because it stops it.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class HealthIntegrationTest {

	@Container
	@ServiceConnection
	static PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"));

	@LocalServerPort
	private int port;

	@Test
	@Order(1)
	void reportsReadyWhileDatabaseIsUp() {
		var response = get();

		assertThat(response.status()).isEqualTo(HttpStatus.OK);
		assertThat(response.body()).isEqualTo("{\"status\":\"ready\"}");
	}

	@Test
	@Order(2)
	void reportsNotReadyWithinTenSecondsOnceDatabaseIsDown() {
		postgres.stop();

		Instant start = Instant.now();
		var response = get();
		Duration elapsed = Duration.between(start, Instant.now());

		assertThat(response.status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
		assertThat(response.body()).isEqualTo("{\"status\":\"not_ready\"}");
		assertThat(elapsed).isLessThan(Duration.ofSeconds(10));
	}

	private Result get() {
		return RestClient.create("http://localhost:" + port)
			.get()
			.uri("/health")
			.exchange((request, response) -> new Result(HttpStatus.valueOf(response.getStatusCode().value()),
					new String(response.getBody().readAllBytes())));
	}

	private record Result(HttpStatus status, String body) {
	}

}
