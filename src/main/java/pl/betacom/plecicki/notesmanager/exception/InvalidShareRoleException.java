package pl.betacom.plecicki.notesmanager.exception;

public class InvalidShareRoleException extends RuntimeException {

    public InvalidShareRoleException() {
        super("Only VIEWER or EDITOR role can be granted");
    }
}