package com.wikigerminare.folders;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice(assignableTypes = FolderController.class)
public class FolderExceptionHandler {

    @ExceptionHandler(FolderNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(
            FolderNotFoundException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(errorBody(exception.getMessage()));
    }

    @ExceptionHandler(FolderValidationException.class)
    public ResponseEntity<Map<String, String>> handleValidationException(
            FolderValidationException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(errorBody(exception.getMessage()));
    }

    @ExceptionHandler(FolderConflictException.class)
    public ResponseEntity<Map<String, String>> handleConflict(
            FolderConflictException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(errorBody(exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleBeanValidation(
            MethodArgumentNotValidException exception
    ) {
        String message = exception.getBindingResult()
                .getAllErrors()
                .stream()
                .findFirst()
                .map(error -> error.getDefaultMessage())
                .orElse("Invalid request");

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(errorBody(message));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> handleDataIntegrityViolation(
            DataIntegrityViolationException exception
    ) {
        String sqlState = findSqlState(exception);

        if ("23503".equals(sqlState)) {
            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(errorBody(
                            "Folder cannot be deleted because it is referenced by another folder"
                    ));
        }

        if ("23505".equals(sqlState)) {
            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(errorBody(
                            "A folder with this name already exists in the same parent"
                    ));
        }

        throw exception;
    }

    private String findSqlState(Throwable exception) {

        Throwable current = exception;

        while (current != null) {

            if (current instanceof SQLException sqlException) {
                return sqlException.getSQLState();
            }

            current = current.getCause();
        }

        return null;
    }

    private Map<String, String> errorBody(String message) {

        Map<String, String> body = new HashMap<>();

        body.put("error", message);

        return body;
    }
}