package com.wikigerminare.service;

public class CommentException extends RuntimeException {
    private final String code;

    public CommentException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
