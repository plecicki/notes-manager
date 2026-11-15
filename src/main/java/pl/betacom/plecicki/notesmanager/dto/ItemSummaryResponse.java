package pl.betacom.plecicki.notesmanager.dto;

import pl.betacom.plecicki.notesmanager.entity.Role;

import java.time.Instant;
import java.util.UUID;

public record ItemSummaryResponse(
        UUID id,
        String title,
        String content,
        int version,
        UUID ownerId,
        Role myRole,
        Instant updatedAt
) {
}