package com.unidoc.request;

public class RequestVersionConflictException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public RequestVersionConflictException() {
        super("The request changed while you were viewing it. Reload and try again.");
    }
}
