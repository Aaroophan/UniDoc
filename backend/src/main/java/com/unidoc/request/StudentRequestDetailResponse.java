package com.unidoc.request;

import com.unidoc.common.api.AttachmentMetadata;
import com.unidoc.common.api.HistoryEntry;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** API-safe detail. Contains no filesystem paths, stored names, JPA entities, or auth secrets. */
public record StudentRequestDetailResponse(
        Long id,
        String requestNumber,
        StudentRequestStudentSummary student,
        Long documentTypeId,
        String documentTypeName,
        String purpose,
        LocalDate requiredDate,
        DeliveryMethod deliveryMethod,
        RequestStatus status,
        String additionalInformationMessage,
        String rejectionReason,
        String collectionLocation,
        String collectionInstructions,
        Instant readyAt,
        Instant completedAt,
        LocalDate collectionDate,
        String collectionNote,
        List<AttachmentMetadata> attachments,
        IssuedDocumentMetadata issuedDocument,
        List<HistoryEntry> history,
        List<RequestAction> availableActions,
        long version,
        Instant createdAt,
        Instant updatedAt
) {
    public StudentRequestDetailResponse {
        attachments = List.copyOf(attachments);
        history = List.copyOf(history);
        availableActions = List.copyOf(availableActions);
    }
}
