package com.unidoc.common.persistence;

import java.util.Locale;
import java.util.Objects;

public final class Normalization {

    private Normalization() {
    }

    public static String email(String value) {
        return required(value).toLowerCase(Locale.ROOT);
    }

    public static String registrationNumber(String value) {
        return required(value).toUpperCase(Locale.ROOT);
    }

    public static String documentCode(String value) {
        return required(value).toUpperCase(Locale.ROOT);
    }

    private static String required(String value) {
        return Objects.requireNonNull(value, "value must not be null").trim();
    }
}
