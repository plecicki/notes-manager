package pl.betacom.plecicki.notesmanager.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.web.servlet.MockMvc;
import pl.betacom.plecicki.notesmanager.config.SecurityConfig;
import pl.betacom.plecicki.notesmanager.dto.LoginResponse;
import pl.betacom.plecicki.notesmanager.dto.RegisterResponse;
import pl.betacom.plecicki.notesmanager.entity.User;
import pl.betacom.plecicki.notesmanager.exception.LoginAlreadyTakenException;
import pl.betacom.plecicki.notesmanager.repository.UserRepository;
import pl.betacom.plecicki.notesmanager.security.JwtAuthenticationFilter;
import pl.betacom.plecicki.notesmanager.security.JwtService;
import pl.betacom.plecicki.notesmanager.security.UserDetailsServiceImpl;
import pl.betacom.plecicki.notesmanager.service.AuthService;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, JwtService.class, JwtAuthenticationFilter.class, UserDetailsServiceImpl.class})
class AuthControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockBean
    private AuthService authService;

    @MockBean
    private UserRepository userRepository;

    @Test
    @DisplayName("POST /register with valid data returns 201")
    void registerShouldReturnCreated() throws Exception {
        RegisterResponse response = new RegisterResponse(UUID.randomUUID(), "jan.kowalski", Instant.now());
        when(authService.register(any())).thenReturn(response);

        mockMvc.perform(post("/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"jan.kowalski\",\"password\":\"password123\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.login").value("jan.kowalski"))
                .andExpect(jsonPath("$.id").exists());
    }

    @Test
    @DisplayName("POST /register with short login returns 400 with field error")
    void registerShouldReturnBadRequestForShortLogin() throws Exception {
        mockMvc.perform(post("/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"ab\",\"password\":\"password123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.fields.login").value("Login must be 3-64 characters long"));
    }

    @Test
    @DisplayName("POST /register with duplicate login returns 409")
    void registerShouldReturnConflict() throws Exception {
        when(authService.register(any())).thenThrow(new LoginAlreadyTakenException("jan.kowalski"));

        mockMvc.perform(post("/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"jan.kowalski\",\"password\":\"password123\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Conflict"));
    }

    @Test
    @DisplayName("POST /login with valid data returns 200 with token")
    void loginShouldReturnToken() throws Exception {
        when(authService.login(any())).thenReturn(new LoginResponse("token-abc", 3600));

        mockMvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"jan.kowalski\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("token-abc"))
                .andExpect(jsonPath("$.expiresIn").value(3600));
    }

    @Test
    @DisplayName("POST /login with wrong password returns 401")
    void loginShouldReturnUnauthorized() throws Exception {
        when(authService.login(any())).thenThrow(new BadCredentialsException("Invalid login or password"));

        mockMvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"jan.kowalski\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Unauthorized"));
    }

    @Test
    @DisplayName("POST /login with empty fields returns 400 with field errors")
    void loginShouldReturnBadRequestForEmptyFields() throws Exception {
        mockMvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.login").value("Field is required"))
                .andExpect(jsonPath("$.fields.password").value("Field is required"));
    }

    @Test
    @DisplayName("protected endpoint without token returns 401")
    void protectedEndpointShouldReturnForbiddenWithoutToken() throws Exception {
        mockMvc.perform(get("/items"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("protected endpoint with valid token passes security")
    void protectedEndpointShouldPassWithValidToken() throws Exception {
        User user = User.builder().login("jan.kowalski").password("encoded").build();
        when(userRepository.findByLogin("jan.kowalski")).thenReturn(Optional.of(user));

        String token = jwtService.generateToken("jan.kowalski");

        mockMvc.perform(get("/items").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("protected endpoint with malformed token returns 401")
    void protectedEndpointShouldReturnForbiddenWithMalformedToken() throws Exception {
        mockMvc.perform(get("/items").header("Authorization", "Bearer not-a-valid-jwt"))
                .andExpect(status().isUnauthorized());
    }
}
