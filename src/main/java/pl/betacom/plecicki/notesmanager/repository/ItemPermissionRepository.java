package pl.betacom.plecicki.notesmanager.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.betacom.plecicki.notesmanager.entity.ItemPermission;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ItemPermissionRepository extends JpaRepository<ItemPermission, UUID> {

    Optional<ItemPermission> findByItemIdAndUserId(UUID itemId, UUID userId);

    List<ItemPermission> findByUserId(UUID userId);

    long deleteByItemIdAndUserId(UUID itemId, UUID userId);
}