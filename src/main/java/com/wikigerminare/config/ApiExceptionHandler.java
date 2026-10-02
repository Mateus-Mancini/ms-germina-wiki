package com.wikigerminare.config;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.TransactionTimedOutException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.wikigerminare.controller.CommentController;
import com.wikigerminare.dto.comment.ErrorResponse;
import com.wikigerminare.service.CommentException;

@RestControllerAdvice(assignableTypes = CommentController.class)
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler({MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class,
            HttpMessageNotReadableException.class, MethodArgumentNotValidException.class})
    public ResponseEntity<ErrorResponse> handleMalformedRequest(Exception exception) {
        return response(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Invalid request body or parameters");
    }

    @ExceptionHandler({QueryTimeoutException.class, TransactionTimedOutException.class,
            CannotAcquireLockException.class, CannotCreateTransactionException.class,
            DataAccessResourceFailureException.class})
    public ResponseEntity<ErrorResponse> handleDatabaseTimeout(Exception exception) {
        log.warn("Comment database operation could not complete", exception);
        return response(HttpStatus.SERVICE_UNAVAILABLE, "COMMENT_STORAGE_UNAVAILABLE",
                "Comment storage could not complete the operation in time");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleValidation(IllegalArgumentException exception) {
        return response(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", exception.getMessage());
    }

    @ExceptionHandler(CommentException.class)
    public ResponseEntity<ErrorResponse> handleComment(CommentException exception) {
        HttpStatus status = switch (exception.code()) {
            case "UNAUTHORIZED" -> HttpStatus.UNAUTHORIZED;
            case "FORBIDDEN" -> HttpStatus.FORBIDDEN;
            case "CONTENT_NOT_FOUND", "COMMENT_NOT_FOUND" -> HttpStatus.NOT_FOUND;
            case "CONTENT_UNAVAILABLE", "ANCHOR_INVALID", "COMMENT_INACTIVE" -> HttpStatus.CONFLICT;
            default -> HttpStatus.BAD_REQUEST;
        };
        return response(status, exception.code(), exception.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception exception) {
        log.error("Unexpected comment API failure", exception);
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Unexpected server error");
    }

    private ResponseEntity<ErrorResponse> response(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(new ErrorResponse(code, message, Map.of()));
    }
}
