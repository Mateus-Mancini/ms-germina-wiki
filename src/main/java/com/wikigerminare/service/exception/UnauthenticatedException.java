package com.wikigerminare.service.exception;

/**
 * Signed-in user required (401).
 */
public class UnauthenticatedException extends RuntimeException {

	public UnauthenticatedException(String message) {
		super(message);
	}

}
