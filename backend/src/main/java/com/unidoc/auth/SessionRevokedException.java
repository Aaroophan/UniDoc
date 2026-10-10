package com.unidoc.auth;

public class SessionRevokedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public SessionRevokedException() {
        super("Your session is no longer valid. Please sign in again.");
    }
}
