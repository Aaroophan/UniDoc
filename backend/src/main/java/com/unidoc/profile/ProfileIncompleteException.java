package com.unidoc.profile;

public class ProfileIncompleteException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ProfileIncompleteException() {
        super("Complete your student profile before submitting a request.");
    }
}
