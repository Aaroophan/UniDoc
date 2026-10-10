package com.unidoc.catalogue;

import java.util.Map;
import java.util.Set;

public final class DocumentTypeSortProperties {

    public static final String DEFAULT = "name";
    public static final Map<String, String> MAPPINGS = Map.of(
            "name", "name",
            "code", "code",
            "createdAt", "createdAt",
            "active", "active"
    );
    public static final Set<String> ALLOWED = MAPPINGS.keySet();

    private DocumentTypeSortProperties() {
    }
}
