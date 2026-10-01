package com.wikigerminare.service.exception;

/**
 * The stored object doesn't match what was allowed (409).
 */
public class UploadMismatchException extends RuntimeException {

	public UploadMismatchException(String message) {
		super(message);
	}

}
