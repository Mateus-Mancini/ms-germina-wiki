package com.wikigerminare.storage;

import java.net.URI;
import java.time.Duration;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.wikigerminare.config.StorageProperties;

import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * {@link ObjectStorage} over the S3-compatible API of Cloudflare R2 (any S3-compatible store works).
 */
@Component
public class R2ObjectStorage implements ObjectStorage {

	private final S3Client s3;

	private final S3Presigner presigner;

	private final String bucket;

	public R2ObjectStorage(S3Client s3, S3Presigner presigner, StorageProperties properties) {
		this.s3 = s3;
		this.presigner = presigner;
		this.bucket = properties.bucket();
	}

	@Override
	public URI presignPut(String key, String contentType, long contentLength, Duration ttl) {
		// Content-Type and Content-Length are signed, so the store rejects a different type or size.
		PutObjectRequest put = PutObjectRequest.builder()
			.bucket(bucket)
			.key(key)
			.contentType(contentType)
			.contentLength(contentLength)
			.build();
		return toUri(presigner.presignPutObject(request -> request.signatureDuration(ttl).putObjectRequest(put)).url());
	}

	@Override
	public URI presignGet(String key, Duration ttl) {
		GetObjectRequest get = GetObjectRequest.builder().bucket(bucket).key(key).build();
		return toUri(presigner.presignGetObject(request -> request.signatureDuration(ttl).getObjectRequest(get)).url());
	}

	@Override
	public Optional<ObjectInfo> head(String key) {
		try {
			HeadObjectResponse head = s3.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build());
			return Optional.of(new ObjectInfo(head.contentType(), head.contentLength()));
		}
		catch (NoSuchKeyException ex) {
			return Optional.empty();
		}
		catch (S3Exception ex) {
			if (ex.statusCode() == 404) {
				return Optional.empty();
			}
			throw ex;
		}
	}

	@Override
	public void copy(String sourceKey, String targetKey) {
		s3.copyObject(CopyObjectRequest.builder()
			.sourceBucket(bucket)
			.sourceKey(sourceKey)
			.destinationBucket(bucket)
			.destinationKey(targetKey)
			.build());
	}

	@Override
	public void delete(String key) {
		s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
	}

	private static URI toUri(java.net.URL url) {
		try {
			return url.toURI();
		}
		catch (java.net.URISyntaxException ex) {
			throw new IllegalStateException("Presigned URL is not a valid URI", ex);
		}
	}

}
