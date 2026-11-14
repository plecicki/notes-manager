package pl.betacom.plecicki.notesmanager.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.betacom.plecicki.notesmanager.dto.LoginRequest;
import pl.betacom.plecicki.notesmanager.dto.LoginResponse;
import pl.betacom.plecicki.notesmanager.dto.RegisterRequest;
import pl.betacom.plecicki.notesmanager.dto.RegisterResponse;
import pl.betacom.plecicki.notesmanager.entity.User;
import pl.betacom.plecicki.notesmanager.exception.LoginAlreadyTakenException;
import pl.betacom.plecicki.notesmanager.repository.UserRepository;
import pl.betacom.plecicki.notesmanager.security.JwtService;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String INVALID_CREDENTIALS = "Invalid login or password";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        if (userRepository.existsByLogin(request.login())) {
            throw new LoginAlreadyTakenException(request.login());
        }

        User user = User.builder()
                .login(request.login())
                .password(passwordEncoder.encode(request.password()))
                .build();
        User saved = userRepository.saveAndFlush(user);

        return new RegisterResponse(saved.getId(), saved.getLogin(), saved.getCreatedAt());
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByLogin(request.login())
                .orElseThrow(() -> new BadCredentialsException(INVALID_CREDENTIALS));

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new BadCredentialsException(INVALID_CREDENTIALS);
        }

        String token = jwtService.generateToken(user.getLogin());
        return new LoginResponse(token, jwtService.getExpirationSeconds());
    }
}
