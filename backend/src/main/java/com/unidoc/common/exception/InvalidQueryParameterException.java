package com.unidoc.common.exception;

public class InvalidQueryParameterException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public InvalidQueryParameterException(String message) {
        super(message);
    }
}
