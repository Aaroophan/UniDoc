package com.unidoc.request;

public class InvalidRequestTransitionException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public InvalidRequestTransitionException() {
        super("This action is not available for the request's current status.");
    }
}
