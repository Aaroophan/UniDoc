package com.unidoc.common.web;

import java.util.Arrays;
import java.util.stream.Collectors;

public final class SafeStackTrace {

    private SafeStackTrace() {
    }

    public static String render(Throwable throwable) {
        if (throwable == null) {
            return "";
        }
        return Arrays.stream(throwable.getStackTrace())
                .map(StackTraceElement::toString)
                .collect(Collectors.joining(
                        System.lineSeparator() + "\tat ",
                        System.lineSeparator() + "\tat ",
                        ""
                ));
    }
}
