package com.unidoc.request;

import com.unidoc.auth.TrimmedSize;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;

/** Optional expectedVersion permits the documented message-only API payload. */
public record InformationResponseRequest(
        @NotBlank(message = "Enter your response to the information request.")
        @TrimmedSize(min = 2, max = 2000, message = "Response must be between 2 and 2000 characters.")
        String message,
        @PositiveOrZero Long expectedVersion
) {
}
