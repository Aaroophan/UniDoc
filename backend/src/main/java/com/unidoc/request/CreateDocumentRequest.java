package com.unidoc.request;

import com.unidoc.auth.TrimmedSize;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;

/** Only user-editable fields; ownership, state, number, and snapshots are server-owned. */
public record CreateDocumentRequest(
        @NotNull(message = "Select a document type.")
        @Positive(message = "Select a valid document type.")
        Long documentTypeId,
        @NotBlank(message = "Enter a purpose for the request.")
        @TrimmedSize(min = 5, max = 1000, message = "Purpose must be between 5 and 1000 characters.")
        String purpose,
        @NotNull(message = "Select a required date.")
        LocalDate requiredDate,
        @NotNull(message = "Select a delivery method.")
        DeliveryMethod deliveryMethod
) {
}
