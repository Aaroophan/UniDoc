package com.unidoc.common.api;

import com.unidoc.request.DeliveryMethod;
import com.unidoc.request.RequestStatus;

import java.time.Instant;
import java.time.LocalDate;

public record RequestSummary(
        Long id,
        String requestNumber,
        String studentName,
        String registrationNumber,
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
