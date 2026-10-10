package com.unidoc.request;

public class IssuedDocumentAlreadyExistsException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public IssuedDocumentAlreadyExistsException() {
        super("An issued document already exists for this request.");
    }
}
