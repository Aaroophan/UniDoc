package com.unidoc.user;

public class SelfDeactivationNotAllowedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public SelfDeactivationNotAllowedException() {
        super("You cannot deactivate your own account.");
    }
}
