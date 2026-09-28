package com.wikigerminare.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.wikigerminare.service.exception.ForbiddenException;
import com.wikigerminare.service.exception.ResourceNotFoundException;
import com.wikigerminare.service.exception.UnauthenticatedException;
import com.wikigerminare.service.exception.UploadMismatchException;
import com.wikigerminare.service.exception.UploadRejectedException;

/**
 * Maps service exceptions to {"error": "..."} responses. Scoped to the controllers listed, so it never changes
 * other features' error handling.
 */
@RestControllerAdvice(assignableTypes = ImageController.class)
public class ApiExceptionHandler {

	@ExceptionHandler(UnauthenticatedException.class)
	ResponseEntity<Map<String, String>> unauthenticated(UnauthenticatedException ex) {
		return error(HttpStatus.UNAUTHORIZED, ex.getMessage());
	}

	@ExceptionHandler(ForbiddenException.class)
	ResponseEntity<Map<String, String>> forbidden(ForbiddenException ex) {
		return error(HttpStatus.FORBIDDEN, ex.getMessage());
	}

	@ExceptionHandler(ResourceNotFoundException.class)
	ResponseEntity<Map<String, String>> notFound(ResourceNotFoundException ex) {
		return error(HttpStatus.NOT_FOUND, ex.getMessage());
	}

	@ExceptionHandler(UploadRejectedException.class)
	ResponseEntity<Map<String, String>> rejected(UploadRejectedException ex) {
		return error(HttpStatus.BAD_REQUEST, ex.getMessage());
	}

	@ExceptionHandler(UploadMismatchException.class)
	ResponseEntity<Map<String, String>> mismatch(UploadMismatchException ex) {
		return error(HttpStatus.CONFLICT, ex.getMessage());
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	ResponseEntity<Map<String, String>> invalid(MethodArgumentNotValidException ex) {
		String message = ex.getBindingResult()
			.getFieldErrors()
			.stream()
			.findFirst()
			.map(fieldError -> fieldError.getDefaultMessage())
			.orElse("Invalid request");
		return error(HttpStatus.BAD_REQUEST, message);
	}

	@ExceptionHandler({ HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class })
	ResponseEntity<Map<String, String>> unreadable(Exception ex) {
		return error(HttpStatus.BAD_REQUEST, "Malformed request");
	}

	private static ResponseEntity<Map<String, String>> error(HttpStatus status, String message) {
		return ResponseEntity.status(status).body(Map.of("error", message));
	}

}
