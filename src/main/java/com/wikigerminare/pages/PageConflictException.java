package com.wikigerminare.pages;

public class PageConflictException extends RuntimeException {

    public PageConflictException(String message) {
        super(message);
    }
}