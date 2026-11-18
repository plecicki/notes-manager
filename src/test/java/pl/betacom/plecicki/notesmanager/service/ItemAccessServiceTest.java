package pl.betacom.plecicki.notesmanager.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.betacom.plecicki.notesmanager.entity.Item;
import pl.betacom.plecicki.notesmanager.entity.ItemPermission;
import pl.betacom.plecicki.notesmanager.entity.Role;
import pl.betacom.plecicki.notesmanager.entity.User;
import pl.betacom.plecicki.notesmanager.repository.ItemPermissionRepository;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ItemAccessServiceTest {

    @Mock
    private ItemPermissionRepository itemPermissionRepository;

    @InjectMocks
    private ItemAccessService itemAccessService;

    private final UUID itemId = UUID.randomUUID();
    private final UUID ownerId = UUID.randomUUID();
    private final UUID otherId = UUID.randomUUID();

    private User user(UUID id) {
        return User.builder().id(id).login("user-" + id).password("pw").build();
    }

    private Item item(UUID owner) {
        return Item.builder().id(itemId).owner(user(owner)).title("title").build();
    }

    @Test
    @DisplayName("isOwner returns true for the owner")
    void isOwnerShouldReturnTrueForOwner() {
        assertThat(itemAccessService.isOwner(item(ownerId), user(ownerId))).isTrue();
    }

    @Test
    @DisplayName("isOwner returns false for another user")
    void isOwnerShouldReturnFalseForOther() {
        assertThat(itemAccessService.isOwner(item(ownerId), user(otherId))).isFalse();
    }

    @Test
    @DisplayName("canEdit returns true for owner")
    void canEditShouldReturnTrueForOwner() {
        assertThat(itemAccessService.canEdit(item(ownerId), user(ownerId))).isTrue();
    }

    @Test
    @DisplayName("canEdit returns true for EDITOR")
    void canEditShouldReturnTrueForEditor() {
        // [GIVEN]
        Item item = item(ownerId);
        when(itemPermissionRepository.findByItemIdAndUserId(itemId, otherId))
                .thenReturn(Optional.of(ItemPermission.builder().item(item).user(user(otherId)).role(Role.EDITOR).build()));

        // [WHEN]
        assertThat(itemAccessService.canEdit(item, user(otherId))).isTrue();
    }

    @Test
    @DisplayName("canEdit returns false for VIEWER")
    void canEditShouldReturnFalseForViewer() {
        // [GIVEN]
        Item item = item(ownerId);
        when(itemPermissionRepository.findByItemIdAndUserId(itemId, otherId))
                .thenReturn(Optional.of(ItemPermission.builder().item(item).user(user(otherId)).role(Role.VIEWER).build()));

        // [WHEN]
        assertThat(itemAccessService.canEdit(item, user(otherId))).isFalse();
    }

    @Test
    @DisplayName("canEdit returns false without permission")
    void canEditShouldReturnFalseWithoutPermission() {
        // [GIVEN]
        when(itemPermissionRepository.findByItemIdAndUserId(itemId, otherId)).thenReturn(Optional.empty());

        // [WHEN]
        assertThat(itemAccessService.canEdit(item(ownerId), user(otherId))).isFalse();
    }

    @Test
    @DisplayName("canRead returns true for owner")
    void canReadShouldReturnTrueForOwner() {
        assertThat(itemAccessService.canRead(item(ownerId), user(ownerId))).isTrue();
    }

    @Test
    @DisplayName("canRead returns true with any permission")
    void canReadShouldReturnTrueWithPermission() {
        // [GIVEN]
        Item item = item(ownerId);
        when(itemPermissionRepository.findByItemIdAndUserId(itemId, otherId))
                .thenReturn(Optional.of(ItemPermission.builder().item(item).user(user(otherId)).role(Role.VIEWER).build()));

        // [WHEN]
        assertThat(itemAccessService.canRead(item, user(otherId))).isTrue();
    }

    @Test
    @DisplayName("canRead returns false without permission")
    void canReadShouldReturnFalseWithoutPermission() {
        // [GIVEN]
        when(itemPermissionRepository.findByItemIdAndUserId(itemId, otherId)).thenReturn(Optional.empty());

        // [WHEN]
        assertThat(itemAccessService.canRead(item(ownerId), user(otherId))).isFalse();
    }

    @Test
    @DisplayName("isOwner returns false when item has no owner")
    void isOwnerShouldReturnFalseWhenOwnerIsNull() {
        // [GIVEN]
        Item item = Item.builder().id(itemId).title("title").build();
        // [WHEN]
        assertThat(itemAccessService.isOwner(item, user(ownerId))).isFalse();
    }

    @Test
    @DisplayName("isOwner returns false for null user")
    void isOwnerShouldReturnFalseForNullUser() {
        assertThat(itemAccessService.isOwner(item(ownerId), null)).isFalse();
    }
}