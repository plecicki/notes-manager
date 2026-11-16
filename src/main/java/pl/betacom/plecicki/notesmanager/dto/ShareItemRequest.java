package pl.betacom.plecicki.notesmanager.dto;

import jakarta.validation.constraints.NotNull;
import pl.betacom.plecicki.notesmanager.entity.Role;

import java.util.UUID;

public record ShareItemRequest(
        @NotNull(message = "{validation.field.required}") UUID userId,
        @NotNull(message = "{validation.field.required}") Role role
) {
}