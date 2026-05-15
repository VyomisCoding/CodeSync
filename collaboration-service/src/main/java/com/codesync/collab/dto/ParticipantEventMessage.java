package com.codesync.collab.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record ParticipantEventMessage(
        UUID sessionId,
        String event,
        ParticipantResponse participant,
        LocalDateTime occurredAt
) {
}
