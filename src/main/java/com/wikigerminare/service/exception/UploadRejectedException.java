package com.wikigerminare.service.exception;

/**
 * The upload request violates the allowed types or sizes (400).
 */
public class UploadRejectedException extends RuntimeException {

	public UploadRejectedException(String message) {
		super(message);
	}

}
