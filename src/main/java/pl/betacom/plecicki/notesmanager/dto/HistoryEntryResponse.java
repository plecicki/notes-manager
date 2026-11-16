package pl.betacom.plecicki.notesmanager.dto;

import java.time.Instant;

public record HistoryEntryResponse(
        int revision,
        String revisionType,
        Instant timestamp,
        String changedBy,
        String title,
        String content
) {
}