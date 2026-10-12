package com.unidoc.request;

/** The recorded physical collection date cannot be later than the local calendar date. */
public class InvalidCollectionDateException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public InvalidCollectionDateException() {
        super("Collection date cannot be in the future.");
    }
}
