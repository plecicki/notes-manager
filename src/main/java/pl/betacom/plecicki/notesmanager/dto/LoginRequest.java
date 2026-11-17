package pl.betacom.plecicki.notesmanager.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record LoginRequest(
        @NotBlank(message = "{validation.field.required}")
        @Pattern(regexp = "^[A-Za-z0-9._-]*$", message = "{validation.login.pattern}")
        String login,
        @NotBlank(message = "{validation.field.required}") String password
) {
}
