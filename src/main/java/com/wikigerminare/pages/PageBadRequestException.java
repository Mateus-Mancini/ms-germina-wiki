package com.wikigerminare.pages;

public class PageBadRequestException
        extends RuntimeException {

    public PageBadRequestException(String message) {
        super(message);
    }
}