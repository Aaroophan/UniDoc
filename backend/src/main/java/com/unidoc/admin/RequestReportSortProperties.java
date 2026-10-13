package com.unidoc.admin;

import java.util.Map;

public final class RequestReportSortProperties {
    public static final Map<String, String> MAPPINGS = Map.of(
            "createdAt", "createdAt", "updatedAt", "updatedAt", "completedAt", "completedAt",
            "requiredDate", "requiredDate", "requestNumber", "requestNumber", "status", "status",
            "deliveryMethod", "deliveryMethod");
    private RequestReportSortProperties() { }
}
