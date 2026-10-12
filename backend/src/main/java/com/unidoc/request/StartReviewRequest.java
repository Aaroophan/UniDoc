package com.unidoc.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/** Version from a fresh detail fetch; never trust a work-list row for mutations. */
public record StartReviewRequest(
        @NotNull @PositiveOrZero Long expectedVersion
) {
}
