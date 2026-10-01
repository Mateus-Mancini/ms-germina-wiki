package com.wikigerminare.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * S3 SDK clients for Cloudflare R2. Built once at startup so they are captured in the SnapStart snapshot;
 * nothing here calls the network.
 * <p>
 * R2 rejects the SDK's default CRC32 checksum headers (SDK >= 2.30) and chunked-encoding signatures, so
 * checksums are only sent when an operation requires them and chunked encoding is disabled
 * (specs/005-image-storage/research.md R2).
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(StorageProperties.class)
public class StorageConfig {

	private static final Region R2_REGION = Region.of("auto");

	private static final S3Configuration R2_S3_CONFIGURATION = S3Configuration.builder()
		.chunkedEncodingEnabled(false)
		.pathStyleAccessEnabled(true)
		.build();

	@Bean(destroyMethod = "close")
	S3Client s3Client(StorageProperties properties) {
		return S3Client.builder()
			.endpointOverride(properties.resolvedEndpoint())
			.region(R2_REGION)
			.credentialsProvider(credentials(properties))
			.serviceConfiguration(R2_S3_CONFIGURATION)
			.requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
			.responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
			.httpClient(UrlConnectionHttpClient.create())
			.build();
	}

	@Bean(destroyMethod = "close")
	S3Presigner s3Presigner(StorageProperties properties) {
		return S3Presigner.builder()
			.endpointOverride(properties.resolvedEndpoint())
			.region(R2_REGION)
			.credentialsProvider(credentials(properties))
			.serviceConfiguration(R2_S3_CONFIGURATION)
			.build();
	}

	private static StaticCredentialsProvider credentials(StorageProperties properties) {
		return StaticCredentialsProvider
			.create(AwsBasicCredentials.create(properties.accessKeyId(), properties.secretAccessKey()));
	}

}
