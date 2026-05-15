package com.codesync.collab.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CursorUpdatePayload(
        @NotNull(message = "cursorLine is required")
        @Min(value = 0, message = "cursorLine must be zero or greater")
        Integer cursorLine,

        @NotNull(message = "cursorCol is required")
        @Min(value = 0, message = "cursorCol must be zero or greater")
        Integer cursorCol
) {
}
