package com.unidoc.request;

public class IssuedDocumentNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public IssuedDocumentNotFoundException() {
        super("The issued document was not found.");
    }
}
