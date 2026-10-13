package com.unidoc.admin;

import com.unidoc.common.api.RequestSummary;
import com.unidoc.request.DeliveryMethod;
import com.unidoc.request.query.DocumentTypeRequestCount;
import java.util.List;
import java.util.Map;

/** Exact dashboard contract from 05-api-design.md; all counts include only committed rows. */
public record AdminDashboardResponse(
        long totalRequests,
        long pendingRequests,
        long approvedRequests,
        long processingRequests,
        long rejectedRequests,
        long readyRequests,
        long completedRequests,
        Map<DeliveryMethod, Long> byDeliveryMethod,
        List<DocumentTypeRequestCount> byDocumentType,
        List<RequestSummary> recentRequests
) {
    public AdminDashboardResponse {
        byDeliveryMethod = Map.copyOf(byDeliveryMethod);
        byDocumentType = List.copyOf(byDocumentType);
        recentRequests = List.copyOf(recentRequests);
    }
}
