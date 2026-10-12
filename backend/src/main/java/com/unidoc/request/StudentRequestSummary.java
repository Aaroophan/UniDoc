package com.unidoc.request;

import java.time.Instant;
import java.time.LocalDate;

/** Student-specific summary intentionally omits redundant student identifiers. */
public record StudentRequestSummary(
        Long id,
        String requestNumber,
        Long documentTypeId,
        String documentTypeName,
        DeliveryMethod deliveryMethod,
        RequestStatus status,
        LocalDate requiredDate,
        Instant createdAt,
        Instant updatedAt,
        long version
) {
}
