package com.wikigerminare.pages;

public class PageValidationException
        extends RuntimeException {

    public PageValidationException(String message) {
        super(message);
    }
}