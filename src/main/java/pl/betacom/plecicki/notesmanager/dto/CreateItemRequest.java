package pl.betacom.plecicki.notesmanager.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateItemRequest(
        @NotBlank(message = "{validation.field.required}")
        @Size(max = 255, message = "{validation.item.title.size}")
        String title,

        String content
) {
}