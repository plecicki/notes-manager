package pl.betacom.plecicki.notesmanager.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pl.betacom.plecicki.notesmanager.entity.Item;

import java.util.List;
import java.util.UUID;

public interface ItemRepository extends JpaRepository<Item, UUID> {

    @Query("SELECT i FROM Item i WHERE i.deleted = false AND (i.owner.id = :userId "
            + "OR EXISTS (SELECT p.id FROM ItemPermission p WHERE p.item = i AND p.user.id = :userId)) "
            + "ORDER BY i.updatedAt DESC")
    List<Item> findAllAccessible(@Param("userId") UUID userId);
}