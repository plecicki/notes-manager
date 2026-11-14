package pl.betacom.plecicki.notesmanager.dto;

public record LoginResponse(String token, long expiresIn) {
}
