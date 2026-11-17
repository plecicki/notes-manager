package pl.betacom.plecicki.notesmanager.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.betacom.plecicki.notesmanager.dto.CreateItemRequest;
import pl.betacom.plecicki.notesmanager.dto.ItemPatchResponse;
import pl.betacom.plecicki.notesmanager.dto.ItemResponse;
import pl.betacom.plecicki.notesmanager.dto.ItemSummaryResponse;
import pl.betacom.plecicki.notesmanager.dto.UpdateItemRequest;
import pl.betacom.plecicki.notesmanager.entity.Item;
import pl.betacom.plecicki.notesmanager.entity.Role;
import pl.betacom.plecicki.notesmanager.entity.User;
import pl.betacom.plecicki.notesmanager.exception.ItemAccessDeniedException;
import pl.betacom.plecicki.notesmanager.exception.ItemNotFoundException;
import pl.betacom.plecicki.notesmanager.exception.ItemVersionConflictException;
import pl.betacom.plecicki.notesmanager.repository.ItemPermissionRepository;
import pl.betacom.plecicki.notesmanager.repository.ItemRepository;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ItemService {

    private final ItemRepository itemRepository;
    private final ItemPermissionRepository itemPermissionRepository;
    private final ItemAccessService itemAccessService;

    @Transactional
    public ItemResponse create(CreateItemRequest request, User owner) {
        Item item = Item.builder()
                .owner(owner)
                .title(request.title().trim())
                .content(request.content())
                .build();
        Item saved = itemRepository.saveAndFlush(item);
        log.info("Item {} created for user '{}'", saved.getId(), owner.getLogin());
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<ItemSummaryResponse> findAll(User user) {
        List<Item> items = itemRepository.findAllAccessible(user.getId());
        Map<UUID, Role> rolesByItem = itemPermissionRepository.findByUserId(user.getId()).stream()
                .collect(Collectors.toMap(
                        permission -> permission.getItem().getId(),
                        permission -> permission.getRole(),
                        (existing, replacement) -> existing));

        List<ItemSummaryResponse> result = items.stream()
                .map(item -> toSummaryResponse(item, user, rolesByItem))
                .toList();
        log.info("Fetched {} items for user '{}'", result.size(), user.getLogin());
        return result;
    }

    @Transactional
    public ItemPatchResponse update(UUID id, UpdateItemRequest request, User user) {
        Item item = findActiveItem(id);

        if (!itemAccessService.canEdit(item, user)) {
            throw new ItemAccessDeniedException();
        }

        if (!Objects.equals(item.getVersion(), request.version())) {
            throw new ItemVersionConflictException(item.getVersion());
        }

        if (request.title() != null) {
            item.setTitle(request.title().trim());
        }
        if (request.content() != null) {
            item.setContent(request.content());
        }

        itemRepository.saveAndFlush(item);
        log.info("User '{}' updated item {} (version {} -> {})", user.getLogin(), id, request.version(), item.getVersion());
        return toPatchResponse(item);
    }

    @Transactional
    public void softDelete(UUID id, User user) {
        Item item = findActiveItem(id);

        if (!itemAccessService.isOwner(item, user)) {
            throw new ItemAccessDeniedException();
        }

        item.setDeleted(true);
        itemRepository.saveAndFlush(item);
        log.info("User '{}' soft-deleted item {}", user.getLogin(), id);
    }

    private Item findActiveItem(UUID id) {
        return itemRepository.findById(id)
                .filter(item -> !item.isDeleted())
                .orElseThrow(() -> new ItemNotFoundException(id));
    }

    private ItemResponse toResponse(Item item) {
        return new ItemResponse(
                item.getId(),
                item.getTitle(),
                item.getContent(),
                item.getVersion(),
                item.getOwner().getId(),
                item.getCreatedAt(),
                item.getUpdatedAt());
    }

    private ItemSummaryResponse toSummaryResponse(Item item, User user, Map<UUID, Role> rolesByItem) {
        Role myRole = itemAccessService.isOwner(item, user)
                ? Role.OWNER
                : rolesByItem.get(item.getId());
        return new ItemSummaryResponse(
                item.getId(),
                item.getTitle(),
                item.getContent(),
                item.getVersion(),
                item.getOwner().getId(),
                myRole,
                item.getUpdatedAt());
    }

    private ItemPatchResponse toPatchResponse(Item item) {
        return new ItemPatchResponse(
                item.getId(),
                item.getTitle(),
                item.getContent(),
                item.getVersion(),
                item.getUpdatedAt());
    }
}