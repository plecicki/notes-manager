package pl.betacom.plecicki.notesmanager.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.betacom.plecicki.notesmanager.dto.GrantResult;
import pl.betacom.plecicki.notesmanager.dto.PermissionResponse;
import pl.betacom.plecicki.notesmanager.dto.ShareItemRequest;
import pl.betacom.plecicki.notesmanager.entity.Item;
import pl.betacom.plecicki.notesmanager.entity.ItemPermission;
import pl.betacom.plecicki.notesmanager.entity.Role;
import pl.betacom.plecicki.notesmanager.entity.User;
import pl.betacom.plecicki.notesmanager.exception.InvalidShareRoleException;
import pl.betacom.plecicki.notesmanager.exception.ItemAccessDeniedException;
import pl.betacom.plecicki.notesmanager.exception.ItemNotFoundException;
import pl.betacom.plecicki.notesmanager.exception.PermissionNotFoundException;
import pl.betacom.plecicki.notesmanager.exception.SelfShareException;
import pl.betacom.plecicki.notesmanager.exception.UserNotFoundException;
import pl.betacom.plecicki.notesmanager.repository.ItemPermissionRepository;
import pl.betacom.plecicki.notesmanager.repository.ItemRepository;
import pl.betacom.plecicki.notesmanager.repository.UserRepository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ItemPermissionService {

    private final ItemRepository itemRepository;
    private final ItemPermissionRepository itemPermissionRepository;
    private final UserRepository userRepository;
    private final ItemAccessService itemAccessService;

    @Transactional
    public GrantResult grant(UUID itemId, ShareItemRequest request, User owner) {
        Item item = findActiveItem(itemId);

        if (!itemAccessService.isOwner(item, owner)) {
            throw new ItemAccessDeniedException();
        }

        if (owner.getId().equals(request.userId())) {
            throw new SelfShareException();
        }

        if (request.role() == Role.OWNER) {
            throw new InvalidShareRoleException();
        }

        User target = userRepository.findById(request.userId())
                .orElseThrow(() -> new UserNotFoundException(request.userId()));

        Optional<ItemPermission> existing = itemPermissionRepository.findByItemIdAndUserId(itemId, request.userId());

        boolean created;
        ItemPermission permission;
        if (existing.isPresent()) {
            permission = existing.get();
            permission.setRole(request.role());
            created = false;
        } else {
            permission = ItemPermission.builder()
                    .item(item)
                    .user(target)
                    .role(request.role())
                    .grantedAt(Instant.now())
                    .build();
            created = true;
        }

        ItemPermission saved = itemPermissionRepository.saveAndFlush(permission);
        log.info("User '{}' {} role {} on item {} for user {}", owner.getLogin(), created ? "granted" : "updated", saved.getRole(), itemId, request.userId());

        return new GrantResult(created, new PermissionResponse(
                saved.getItem().getId(),
                saved.getUser().getId(),
                saved.getRole(),
                saved.getGrantedAt()));
    }

    @Transactional
    public void revoke(UUID itemId, UUID userId, User owner) {
        Item item = findActiveItem(itemId);

        if (!itemAccessService.isOwner(item, owner)) {
            throw new ItemAccessDeniedException();
        }

        long deleted = itemPermissionRepository.deleteByItemIdAndUserId(itemId, userId);
        if (deleted == 0) {
            throw new PermissionNotFoundException();
        }
        log.info("User '{}' revoked access to item {} from user {}", owner.getLogin(), itemId, userId);
    }

    private Item findActiveItem(UUID id) {
        return itemRepository.findById(id)
                .filter(item -> !item.isDeleted())
                .orElseThrow(() -> new ItemNotFoundException(id));
    }
}