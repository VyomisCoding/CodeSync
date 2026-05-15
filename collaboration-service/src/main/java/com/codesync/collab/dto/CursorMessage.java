package com.codesync.collab.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record CursorMessage(
        UUID sessionId,
        Long userId,
        Integer cursorLine,
        Integer cursorCol,
        String color,
        LocalDateTime updatedAt
) {
}
