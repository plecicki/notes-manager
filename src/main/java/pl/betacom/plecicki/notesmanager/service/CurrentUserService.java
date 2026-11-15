package pl.betacom.plecicki.notesmanager.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import pl.betacom.plecicki.notesmanager.entity.User;
import pl.betacom.plecicki.notesmanager.repository.UserRepository;

@Service
@RequiredArgsConstructor
public class CurrentUserService {

    private final UserRepository userRepository;

    public User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new BadCredentialsException("Not authenticated");
        }
        return userRepository.findByLogin(authentication.getName())
                .orElseThrow(() -> new BadCredentialsException("Invalid login or password"));
    }
}