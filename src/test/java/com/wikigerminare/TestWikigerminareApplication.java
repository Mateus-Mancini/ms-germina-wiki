package com.wikigerminare;

import org.springframework.boot.SpringApplication;

/**
 * Local entry point: {@code ./mvnw spring-boot:test-run} starts the API with a throwaway PostgreSQL container.
 */
public class TestWikigerminareApplication {

	public static void main(String[] args) {
		SpringApplication.from(WikigerminareApplication::main)
				.with(TestcontainersConfiguration.class)
				.withAdditionalProfiles("local")
				.run(args);
	}

}
