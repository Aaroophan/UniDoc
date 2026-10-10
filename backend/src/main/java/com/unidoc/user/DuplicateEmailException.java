package com.unidoc.user;

public class DuplicateEmailException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public DuplicateEmailException() {
        super("An account already uses this email address.");
    }
}
