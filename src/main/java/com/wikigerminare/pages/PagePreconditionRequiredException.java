package com.wikigerminare.pages;

public class PagePreconditionRequiredException extends RuntimeException {

    public PagePreconditionRequiredException(String message) {
        super(message);
    }
}