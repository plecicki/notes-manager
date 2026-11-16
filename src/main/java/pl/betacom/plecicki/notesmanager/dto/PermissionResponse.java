package pl.betacom.plecicki.notesmanager.dto;

import pl.betacom.plecicki.notesmanager.entity.Role;

import java.time.Instant;
import java.util.UUID;

public record PermissionResponse(
        UUID itemId,
        UUID userId,
        Role role,
        Instant grantedAt
) {
}