package com.unidoc.common.api;

import com.unidoc.request.RequestStatus;

import java.time.Instant;

public record HistoryEntry(
        Long id,
        RequestStatus previousStatus,
        RequestStatus newStatus,
        ActorSummary changedBy,
        String note,
        Instant createdAt
) {
}
