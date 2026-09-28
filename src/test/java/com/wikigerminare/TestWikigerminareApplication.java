package com.wikigerminare;

import org.springframework.boot.SpringApplication;

/**
 * Local entry point: {@code ./mvnw spring-boot:test-run} starts the API with throwaway PostgreSQL and MinIO
 * (S3-compatible storage) containers.
 */
public class TestWikigerminareApplication {

	public static void main(String[] args) {
		SpringApplication.from(WikigerminareApplication::main)
				.with(TestcontainersConfiguration.class, StorageTestcontainersConfiguration.class)
				.withAdditionalProfiles("local")
				.run(args);
	}

}
