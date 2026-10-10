package com.unidoc.catalogue;

public class DocumentTypeNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public DocumentTypeNotFoundException() {
        super("The document type was not found.");
    }
}
