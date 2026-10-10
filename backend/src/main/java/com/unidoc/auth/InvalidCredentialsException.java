package com.unidoc.auth;

public class InvalidCredentialsException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public InvalidCredentialsException() {
        super("Email or password is incorrect.");
    }
}
