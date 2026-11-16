package pl.betacom.plecicki.notesmanager.exception;

public class PermissionNotFoundException extends RuntimeException {

    public PermissionNotFoundException() {
        super("Permission not found");
    }
}