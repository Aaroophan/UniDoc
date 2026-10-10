package com.unidoc.catalogue;

import java.util.Set;

public final class DocumentTypeSortProperties {

    public static final String DEFAULT = "name";
    public static final Set<String> ALLOWED = Set.of("name", "code", "createdAt", "active");

    private DocumentTypeSortProperties() {
    }
}
