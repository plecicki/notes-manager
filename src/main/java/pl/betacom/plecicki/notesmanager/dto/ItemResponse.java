package pl.betacom.plecicki.notesmanager.dto;

import java.time.Instant;
import java.util.UUID;

public record ItemResponse(
        UUID id,
        String title,
        String content,
        int version,
        UUID ownerId,
        Instant createdAt,
        Instant updatedAt
) {
}