package com.wikigerminare.search;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = SearchController.class)
public class SearchExceptionHandler {

    @ExceptionHandler({
            SearchValidationException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class,
            MethodArgumentNotValidException.class
    })
    public ResponseEntity<Map<String, String>> handleBadRequest(Exception exception) {
        String message;

        if (exception instanceof SearchValidationException validationException) {
            message = validationException.getMessage();
        } else if (exception instanceof MissingServletRequestParameterException missingParameter) {
            message = "Required request parameter '%s' is missing"
                    .formatted(missingParameter.getParameterName());
        } else if (exception instanceof MethodArgumentTypeMismatchException typeMismatch) {
            message = "Invalid value for request parameter '%s'"
                    .formatted(typeMismatch.getName());
        } else if (exception instanceof MethodArgumentNotValidException validationException) {
            message = validationException.getBindingResult()
                    .getFieldErrors()
                    .stream()
                    .findFirst()
                    .map(error -> error.getDefaultMessage())
                    .orElse("Invalid request");
        } else {
            message = "Invalid request";
        }

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", message));
    }
}
