package pl.betacom.plecicki.notesmanager.exception;

import java.util.Map;

public record ErrorResponse(String error, String message, Map<String, String> fields) {

    public ErrorResponse(String error, String message) {
        this(error, message, Map.of());
    }
}
