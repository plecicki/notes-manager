package pl.betacom.plecicki.notesmanager.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateItemRequest(
        // Optional title: null means "leave unchanged", but it must not be blank when provided.
        @Size(max = 255, message = "{validation.item.title.size}")
        @Pattern(regexp = ".*\\S.*", message = "{validation.item.title.required}")
        String title,

        String content,

        @NotNull(message = "{validation.item.version.required}")
        Integer version
) {
}