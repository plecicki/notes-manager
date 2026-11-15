package pl.betacom.plecicki.notesmanager.exception;

public class ItemVersionConflictException extends RuntimeException {

    private final int currentVersion;

    public ItemVersionConflictException(int currentVersion) {
        super("Item version conflict. Current version: " + currentVersion);
        this.currentVersion = currentVersion;
    }

    public int getCurrentVersion() {
        return currentVersion;
    }
}