package pl.betacom.plecicki.notesmanager.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class LoginRequestTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
    }

    @Test
    @DisplayName("should pass for valid data")
    void shouldPassForValidData() {
        assertThat(violations("jan.kowalski", "password123")).isEmpty();
    }

    @Test
    @DisplayName("should fail for blank login")
    void shouldFailForBlankLogin() {
        assertThat(violations("", "password123")).isNotEmpty();
    }

    @Test
    @DisplayName("should fail for null login")
    void shouldFailForNullLogin() {
        assertThat(violations(null, "password123")).isNotEmpty();
    }

    @Test
    @DisplayName("should fail for blank password")
    void shouldFailForBlankPassword() {
        assertThat(violations("jan.kowalski", "")).isNotEmpty();
    }

    @Test
    @DisplayName("should fail for null password")
    void shouldFailForNullPassword() {
        assertThat(violations("jan.kowalski", null)).isNotEmpty();
    }

    private Set<ConstraintViolation<LoginRequest>> violations(String login, String password) {
        return validator.validate(new LoginRequest(login, password));
    }
}
