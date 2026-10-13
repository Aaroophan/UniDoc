package com.unidoc.admin;

import com.unidoc.common.api.AttachmentMetadata;
import com.unidoc.common.api.HistoryEntry;
import com.unidoc.request.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Monitoring-only request details; metadata is visible, file bytes and staff actions are not. */
public record AdminRequestDetailResponse(
        Long id, String requestNumber, StaffRequestStudentSummary student,
        Long documentTypeId, String documentTypeName, String purpose, LocalDate requiredDate,
        DeliveryMethod deliveryMethod, RequestStatus status,
        String additionalInformationMessage, String rejectionReason,
        String collectionLocation, String collectionInstructions,
        Instant readyAt, Instant completedAt, LocalDate collectionDate, String collectionNote,
        List<AttachmentMetadata> attachments, IssuedDocumentMetadata issuedDocument,
        List<HistoryEntry> history, List<RequestAction> availableActions,
        long version, Instant createdAt, Instant updatedAt) {
    public AdminRequestDetailResponse {
        attachments = List.copyOf(attachments);
        history = List.copyOf(history);
        availableActions = List.of(); // Even if a caller accidentally supplied staff actions.
    }
}
