package com.unidoc.catalogue;

import jakarta.validation.constraints.NotNull;

public record ChangeDocumentTypeStatusRequest(
        @NotNull(message = "Active status is required.") Boolean active
) { }
