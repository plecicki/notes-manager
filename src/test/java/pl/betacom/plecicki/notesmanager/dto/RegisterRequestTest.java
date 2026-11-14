package pl.betacom.plecicki.notesmanager.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class RegisterRequestTest {

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
    @DisplayName("should fail for null login")
    void shouldFailForNullLogin() {
        assertThat(violations(null, "password123")).isNotEmpty();
    }

    @Test
    @DisplayName("should fail for blank login")
    void shouldFailForBlankLogin() {
        assertThat(violations("   ", "password123")).isNotEmpty();
    }

    @Test
    @DisplayName("should fail for login shorter than 3 chars")
    void shouldFailForTooShortLogin() {
        assertThat(violations("ab", "password123")).isNotEmpty();
    }

    @Test
    @DisplayName("should pass for login of exactly 3 chars")
    void shouldPassForMinLengthLogin() {
        assertThat(violations("abc", "password123")).isEmpty();
    }

    @Test
    @DisplayName("should pass for login of exactly 64 chars")
    void shouldPassForMaxLengthLogin() {
        assertThat(violations("a".repeat(64), "password123")).isEmpty();
    }

    @Test
    @DisplayName("should fail for login longer than 64 chars")
    void shouldFailForTooLongLogin() {
        assertThat(violations("a".repeat(65), "password123")).isNotEmpty();
    }

    @Test
    @DisplayName("should fail for password shorter than 8 chars")
    void shouldFailForTooShortPassword() {
        assertThat(violations("jan.kowalski", "1234567")).isNotEmpty();
    }

    @Test
    @DisplayName("should pass for password of exactly 8 chars")
    void shouldPassForMinLengthPassword() {
        assertThat(violations("jan.kowalski", "12345678")).isEmpty();
    }

    @Test
    @DisplayName("should fail for null password")
    void shouldFailForNullPassword() {
        assertThat(violations("jan.kowalski", null)).isNotEmpty();
    }

    @Test
    @DisplayName("should fail for blank password")
    void shouldFailForBlankPassword() {
        assertThat(violations("jan.kowalski", "   ")).isNotEmpty();
    }

    private Set<ConstraintViolation<RegisterRequest>> violations(String login, String password) {
        return validator.validate(new RegisterRequest(login, password));
    }
}
