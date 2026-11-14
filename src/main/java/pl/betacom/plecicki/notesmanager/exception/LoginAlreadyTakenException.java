package pl.betacom.plecicki.notesmanager.exception;

public class LoginAlreadyTakenException extends RuntimeException {

    private final String login;

    public LoginAlreadyTakenException(String login) {
        super("Login already taken: " + login);
        this.login = login;
    }

    public String getLogin() {
        return login;
    }
}
