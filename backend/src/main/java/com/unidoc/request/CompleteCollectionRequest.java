package com.unidoc.request;

import com.unidoc.auth.TrimmedSize;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.time.LocalDate;

/** Staff's dated, terminal physical-collection confirmation. No collector identity or uploads. */
public record CompleteCollectionRequest(
        @NotNull(message = "Collection date is required.") LocalDate collectionDate,
        @TrimmedSize(max = 1000, message = "Collection note cannot exceed 1000 characters.") String note,
        @NotNull @PositiveOrZero Long expectedVersion
) {
}
