package com.unidoc.request;

import com.unidoc.auth.TrimmedSize;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/** A reason is mandatory because rejection is terminal. */
public record RejectRequest(
        @NotBlank(message = "Provide a reason for rejection.")
        @TrimmedSize(min = 5, max = 2000, message = "Reason must be between 5 and 2000 characters.")
        String reason,
        @NotNull @PositiveOrZero Long expectedVersion
) {
}
