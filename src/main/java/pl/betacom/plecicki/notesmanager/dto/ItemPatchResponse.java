package pl.betacom.plecicki.notesmanager.dto;

import java.time.Instant;
import java.util.UUID;

public record ItemPatchResponse(
        UUID id,
        String title,
        String content,
        int version,
        Instant updatedAt
) {
}