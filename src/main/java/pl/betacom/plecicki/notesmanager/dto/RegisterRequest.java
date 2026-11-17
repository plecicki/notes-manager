package pl.betacom.plecicki.notesmanager.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "{validation.field.required}")
        @Size(min = 3, max = 64, message = "{validation.login.size}")
        @Pattern(regexp = "^[A-Za-z0-9._-]*$", message = "{validation.login.pattern}")
        String login,

        @NotBlank(message = "{validation.field.required}")
        @Size(min = 8, message = "{validation.password.size}")
        String password
) {
}
