package com.unidoc.file;

public class AttachmentNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public AttachmentNotFoundException() {
        super("The attachment was not found.");
    }
}
