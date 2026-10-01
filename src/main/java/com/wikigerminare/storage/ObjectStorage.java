package com.wikigerminare.storage;

import java.net.URI;
import java.time.Duration;
import java.util.Optional;

/**
 * Port for the image object store (Cloudflare R2 in production, MinIO in tests). Services depend on this
 * interface, never on the SDK.
 */
public interface ObjectStorage {

	/**
	 * A URL that lets a browser PUT exactly one object with the given content type and length.
	 */
	URI presignPut(String key, String contentType, long contentLength, Duration ttl);

	/**
	 * A short-lived URL to GET one object.
	 */
	URI presignGet(String key, Duration ttl);

	/**
	 * Metadata of an object, or empty if it doesn't exist.
	 */
	Optional<ObjectInfo> head(String key);

	void copy(String sourceKey, String targetKey);

	/**
	 * Deletes an object; deleting a missing object is not an error.
	 */
	void delete(String key);

	record ObjectInfo(String contentType, long contentLength) {
	}

}
