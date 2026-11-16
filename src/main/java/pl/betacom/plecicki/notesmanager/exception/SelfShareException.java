package pl.betacom.plecicki.notesmanager.exception;

public class SelfShareException extends RuntimeException {

    public SelfShareException() {
        super("Cannot share an item with yourself");
    }
}