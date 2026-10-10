package com.unidoc.auth;

public class InactiveAccountException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public InactiveAccountException() {
        super("This account is inactive. Contact an administrator.");
    }
}
