package pl.betacom.plecicki.notesmanager.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import pl.betacom.plecicki.notesmanager.entity.User;
import pl.betacom.plecicki.notesmanager.repository.UserRepository;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserDetailsServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserDetailsServiceImpl userDetailsService;

    @Test
    @DisplayName("should return user details when user exists")
    void shouldReturnUserDetailsWhenUserExists() {
        User user = User.builder().login("jan.kowalski").password("encoded").build();
        when(userRepository.findByLogin("jan.kowalski")).thenReturn(Optional.of(user));

        UserDetails details = userDetailsService.loadUserByUsername("jan.kowalski");

        assertThat(details.getUsername()).isEqualTo("jan.kowalski");
        assertThat(details.getPassword()).isEqualTo("encoded");
    }

    @Test
    @DisplayName("should throw when user does not exist")
    void shouldThrowWhenUserDoesNotExist() {
        when(userRepository.findByLogin("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userDetailsService.loadUserByUsername("unknown"))
                .isInstanceOf(UsernameNotFoundException.class);
    }
}
