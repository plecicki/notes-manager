package pl.betacom.plecicki.notesmanager.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "{validation.field.required}") String login,
        @NotBlank(message = "{validation.field.required}") String password
) {
}
