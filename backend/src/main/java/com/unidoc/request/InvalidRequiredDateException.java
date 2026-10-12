package com.unidoc.request;

public class InvalidRequiredDateException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public InvalidRequiredDateException() {
        super("Required date must be today or later.");
    }
}
