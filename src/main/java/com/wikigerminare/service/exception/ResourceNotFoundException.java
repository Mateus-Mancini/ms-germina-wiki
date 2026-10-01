package com.wikigerminare.service.exception;

/**
 * A page, image or upload does not exist (404).
 */
public class ResourceNotFoundException extends RuntimeException {

	public ResourceNotFoundException(String message) {
		super(message);
	}

}
