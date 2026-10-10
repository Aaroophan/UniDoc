package com.unidoc.request;

import java.util.Map;
import java.util.Set;

public final class DocumentRequestSortProperties {

    public static final String DEFAULT = "createdAt";
    public static final Map<String, String> MAPPINGS = Map.of(
            "createdAt", "createdAt",
            "updatedAt", "updatedAt",
            "requiredDate", "requiredDate",
            "status", "status",
            "requestNumber", "requestNumber"
    );
    public static final Set<String> ALLOWED = MAPPINGS.keySet();

    private DocumentRequestSortProperties() {
    }
}
