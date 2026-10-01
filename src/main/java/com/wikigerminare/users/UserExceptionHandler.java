package com.wikigerminare.users;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.http.converter.HttpMessageNotReadableException;

import java.util.Map;

@RestControllerAdvice(assignableTypes = UserController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class UserExceptionHandler {

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(UserNotFoundException exception) {
        return response(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(UserUnauthorizedException.class)
    public ResponseEntity<Map<String, String>> handleUnauthorized(UserUnauthorizedException exception) {
        return response(HttpStatus.UNAUTHORIZED, exception.getMessage());
    }

    @ExceptionHandler(UserValidationException.class)
    public ResponseEntity<Map<String, String>> handleValidation(UserValidationException exception) {
        return response(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class
    })
    public ResponseEntity<Map<String, String>> handleInvalidRequest(Exception exception) {
        String message = exception instanceof MethodArgumentNotValidException validationException
                ? validationException.getBindingResult().getAllErrors().stream()
                        .findFirst()
                        .map(error -> error.getDefaultMessage())
                        .orElse("Invalid request")
                : "Invalid request";
        return response(HttpStatus.BAD_REQUEST, message);
    }

    private ResponseEntity<Map<String, String>> response(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("error", message));
    }
}
