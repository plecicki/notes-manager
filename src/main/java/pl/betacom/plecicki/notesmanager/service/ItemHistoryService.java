package pl.betacom.plecicki.notesmanager.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.envers.AuditReader;
import org.hibernate.envers.AuditReaderFactory;
import org.hibernate.envers.RevisionType;
import org.hibernate.envers.query.AuditEntity;
import org.hibernate.envers.query.AuditQuery;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.betacom.plecicki.notesmanager.dto.HistoryEntryResponse;
import pl.betacom.plecicki.notesmanager.entity.AuditRevisionEntity;
import pl.betacom.plecicki.notesmanager.entity.Item;
import pl.betacom.plecicki.notesmanager.entity.User;
import pl.betacom.plecicki.notesmanager.exception.ItemAccessDeniedException;
import pl.betacom.plecicki.notesmanager.exception.ItemNotFoundException;
import pl.betacom.plecicki.notesmanager.repository.ItemRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ItemHistoryService {

    private final ItemRepository itemRepository;
    private final ItemAccessService itemAccessService;

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional(readOnly = true)
    public List<HistoryEntryResponse> findHistory(UUID itemId, User user) {
        Item item = itemRepository.findById(itemId)
                .orElseThrow(() -> new ItemNotFoundException(itemId));

        if (!itemAccessService.canRead(item, user)) {
            throw new ItemAccessDeniedException();
        }

        AuditReader reader = AuditReaderFactory.get(entityManager);
        AuditQuery query = reader.createQuery()
                .forRevisionsOfEntity(Item.class, false, true)
                .add(AuditEntity.id().eq(itemId));

        List<HistoryEntryResponse> history = query.getResultList().stream()
                .map(row -> toHistoryEntry((Object[]) row))
                .toList();
        log.info("User '{}' fetched {} revisions of item {}", user.getLogin(), history.size(), itemId);
        return history;
    }

    private HistoryEntryResponse toHistoryEntry(Object[] row) {
        Item snapshot = (Item) row[0];
        AuditRevisionEntity revision = (AuditRevisionEntity) row[1];
        RevisionType type = (RevisionType) row[2];
        String revisionType = snapshot.isDeleted() ? "DEL" : type.name();

        return new HistoryEntryResponse(
                revision.getRev(),
                revisionType,
                Instant.ofEpochMilli(revision.getTimestamp()),
                revision.getChangedBy(),
                snapshot.getTitle(),
                snapshot.getContent());
    }
}