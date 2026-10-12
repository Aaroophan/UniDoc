package com.unidoc.request;

public class InvalidDeliveryMethodException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public InvalidDeliveryMethodException() {
        super("The selected delivery method is not offered for this document type.");
    }
}
