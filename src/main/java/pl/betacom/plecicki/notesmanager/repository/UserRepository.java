package pl.betacom.plecicki.notesmanager.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.betacom.plecicki.notesmanager.entity.User;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByLogin(String login);

    boolean existsByLogin(String login);
}
