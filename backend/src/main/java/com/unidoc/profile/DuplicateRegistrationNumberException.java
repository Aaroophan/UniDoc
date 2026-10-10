package com.unidoc.profile;

public class DuplicateRegistrationNumberException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public DuplicateRegistrationNumberException() {
        super("This registration number is already in use.");
    }
}
