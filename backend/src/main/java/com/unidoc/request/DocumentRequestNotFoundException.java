package com.unidoc.request;

public class DocumentRequestNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public DocumentRequestNotFoundException() {
        super("The document request was not found.");
    }
}
