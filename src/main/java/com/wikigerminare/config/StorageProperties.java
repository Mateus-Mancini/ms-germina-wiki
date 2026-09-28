package com.wikigerminare.config;

import java.net.URI;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;

/**
 * Object storage (Cloudflare R2) settings, bound from APP_STORAGE_* environment variables in production.
 *
 * @param accountId R2 account id; used to build the endpoint when {@code endpoint} is not set
 * @param bucket private bucket holding page images
 * @param accessKeyId bucket-scoped R2 API token key id
 * @param secretAccessKey bucket-scoped R2 API token secret
 * @param endpoint optional endpoint override (tests use MinIO)
 */
@Validated
@ConfigurationProperties("app.storage")
public record StorageProperties(String accountId, @NotBlank String bucket, @NotBlank String accessKeyId,
		@NotBlank String secretAccessKey, URI endpoint) {

	public URI resolvedEndpoint() {
		if (endpoint != null) {
			return endpoint;
		}
		if (accountId == null || accountId.isBlank()) {
			throw new IllegalStateException("app.storage.account-id or app.storage.endpoint must be set");
		}
		return URI.create("https://" + accountId + ".r2.cloudflarestorage.com");
	}

}
