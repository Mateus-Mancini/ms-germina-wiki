package com.wikigerminare.service.exception;

/**
 * The user may not perform this action on the resource (403).
 */
public class ForbiddenException extends RuntimeException {

	public ForbiddenException(String message) {
		super(message);
	}

}
