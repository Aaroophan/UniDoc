package com.unidoc.request;

import com.unidoc.auth.TrimmedSize;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/** Staff's explanation is visible to the student and preserved in history. */
public record RequestInformationRequest(
        @NotBlank(message = "Explain what information is needed.")
        @TrimmedSize(min = 5, max = 2000, message = "Message must be between 5 and 2000 characters.")
        String message,
        @NotNull @PositiveOrZero Long expectedVersion
) {
}
