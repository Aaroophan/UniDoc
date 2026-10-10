package com.unidoc.request;

public class DeliveryMethodMismatchException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public DeliveryMethodMismatchException() {
        super("This action is not available for the request's delivery method.");
    }
}
