package com.wikigerminare.pages;

import com.wikigerminare.folders.FolderNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class PageExceptionHandler {

    @ExceptionHandler(PageNotFoundException.class)
    public ResponseEntity<Map<String, String>> handlePageNotFound(
            PageNotFoundException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(errorBody(exception.getMessage()));
    }

    @ExceptionHandler(FolderNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleFolderNotFound(
            FolderNotFoundException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(errorBody(exception.getMessage()));
    }

    @ExceptionHandler(PageConflictException.class)
    public ResponseEntity<Map<String, String>> handleConflict(
            PageConflictException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(errorBody(exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(
            MethodArgumentNotValidException exception
    ) {
        String message = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .findFirst()
                .map(error -> error.getDefaultMessage())
                .orElse("Invalid request");

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(errorBody(message));
    }

    private Map<String, String> errorBody(String message) {

        Map<String, String> body = new HashMap<>();

        body.put("error", message);

        return body;
    }
}