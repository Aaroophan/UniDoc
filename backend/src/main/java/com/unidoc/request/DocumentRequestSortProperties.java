package com.unidoc.request;

import java.util.Set;

public final class DocumentRequestSortProperties {

    public static final String DEFAULT = "createdAt";
    public static final Set<String> ALLOWED = Set.of(
            "createdAt",
            "updatedAt",
            "requiredDate",
            "status",
            "requestNumber"
    );

    private DocumentRequestSortProperties() {
    }
}
