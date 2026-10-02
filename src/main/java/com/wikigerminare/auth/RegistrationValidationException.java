package com.wikigerminare.auth;

public class RegistrationValidationException extends RuntimeException {
    public RegistrationValidationException() {
        super("Invalid request");
    }
}
