package pl.betacom.plecicki.notesmanager.exception;

public class ItemAccessDeniedException extends RuntimeException {

    public ItemAccessDeniedException() {
        super("Access to item denied");
    }
}