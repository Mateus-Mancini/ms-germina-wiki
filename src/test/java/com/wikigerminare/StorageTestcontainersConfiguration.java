package com.wikigerminare;

import java.net.URI;
import java.util.Map;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.utility.DockerImageName;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * S3-compatible object storage (MinIO) for image tests and local runs, with its bucket created and
 * app.storage.* pointed at it.
 */
@TestConfiguration(proxyBeanMethods = false)
public class StorageTestcontainersConfiguration {

	public static final String BUCKET = "germinawiki-images-test";

	@Bean
	MinIOContainer minioContainer() {
		// minio/minio is no longer published on Docker Hub; Chainguard maintains a MinIO build.
		// Memory-backed data dir: faster, and independent of free disk space on the host.
		return new MinIOContainer(
				DockerImageName.parse("chainguard/minio:latest").asCompatibleSubstituteFor("minio/minio"))
			.withTmpFs(Map.of("/data", "rw,size=256m"));
	}

	@Bean
	DynamicPropertyRegistrar storageProperties(MinIOContainer minio) {
		return registry -> {
			createBucket(minio);
			registry.add("app.storage.endpoint", minio::getS3URL);
			registry.add("app.storage.bucket", () -> BUCKET);
			registry.add("app.storage.access-key-id", minio::getUserName);
			registry.add("app.storage.secret-access-key", minio::getPassword);
		};
	}

	private static void createBucket(MinIOContainer minio) {
		if (!minio.isRunning()) {
			minio.start();
		}
		try (S3Client admin = S3Client.builder()
			.endpointOverride(URI.create(minio.getS3URL()))
			.region(Region.US_EAST_1)
			.forcePathStyle(true)
			.credentialsProvider(StaticCredentialsProvider
				.create(AwsBasicCredentials.create(minio.getUserName(), minio.getPassword())))
			.httpClient(UrlConnectionHttpClient.create())
			.build()) {
			if (admin.listBuckets().buckets().stream().noneMatch(bucket -> bucket.name().equals(BUCKET))) {
				admin.createBucket(request -> request.bucket(BUCKET));
			}
		}
	}

}
