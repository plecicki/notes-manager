package pl.betacom.plecicki.notesmanager.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import pl.betacom.plecicki.notesmanager.dto.LoginRequest;
import pl.betacom.plecicki.notesmanager.dto.LoginResponse;
import pl.betacom.plecicki.notesmanager.dto.RegisterRequest;
import pl.betacom.plecicki.notesmanager.dto.RegisterResponse;
import pl.betacom.plecicki.notesmanager.entity.User;
import pl.betacom.plecicki.notesmanager.exception.LoginAlreadyTakenException;
import pl.betacom.plecicki.notesmanager.repository.UserRepository;
import pl.betacom.plecicki.notesmanager.security.JwtService;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtService jwtService;

    @InjectMocks
    private AuthService authService;

    @Test
    @DisplayName("register should save user with encoded password")
    void registerShouldSaveEncodedPassword() {
        // [GIVEN]
        when(userRepository.existsByLogin("jan.kowalski")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("ENCODED");
        User saved = User.builder().login("jan.kowalski").password("ENCODED").build();
        when(userRepository.saveAndFlush(any(User.class))).thenReturn(saved);

        // [WHEN]
        RegisterResponse response = authService.register(new RegisterRequest("jan.kowalski", "password123"));

        // [THEN]
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getPassword()).isEqualTo("ENCODED");
        assertThat(response.login()).isEqualTo("jan.kowalski");
    }

    @Test
    @DisplayName("register should throw when login already exists")
    void registerShouldThrowOnDuplicateLogin() {
        // [GIVEN]
        when(userRepository.existsByLogin("jan.kowalski")).thenReturn(true);

        // [WHEN]
        assertThatThrownBy(() -> authService.register(new RegisterRequest("jan.kowalski", "password123")))
                .isInstanceOf(LoginAlreadyTakenException.class);

        // [THEN]
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("login should return token and expiration")
    void loginShouldReturnToken() {
        // [GIVEN]
        User user = User.builder().login("jan.kowalski").password("ENCODED").build();
        when(userRepository.findByLogin("jan.kowalski")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", "ENCODED")).thenReturn(true);
        when(jwtService.generateToken("jan.kowalski")).thenReturn("token-abc");
        when(jwtService.getExpirationSeconds()).thenReturn(3600L);

        // [WHEN]
        LoginResponse response = authService.login(new LoginRequest("jan.kowalski", "password123"));

        // [THEN]
        assertThat(response.token()).isEqualTo("token-abc");
        assertThat(response.expiresIn()).isEqualTo(3600);
    }

    @Test
    @DisplayName("login should throw on unknown user")
    void loginShouldThrowOnUnknownUser() {
        // [GIVEN]
        when(userRepository.findByLogin("unknown")).thenReturn(Optional.empty());

        // [WHEN]
        assertThatThrownBy(() -> authService.login(new LoginRequest("unknown", "password123")))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    @DisplayName("login should throw on wrong password")
    void loginShouldThrowOnWrongPassword() {
        // [GIVEN]
        User user = User.builder().login("jan.kowalski").password("ENCODED").build();
        when(userRepository.findByLogin("jan.kowalski")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "ENCODED")).thenReturn(false);

        // [WHEN]
        assertThatThrownBy(() -> authService.login(new LoginRequest("jan.kowalski", "wrong")))
                .isInstanceOf(BadCredentialsException.class);
    }
}
