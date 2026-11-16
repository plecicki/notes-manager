package pl.betacom.plecicki.notesmanager.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.betacom.plecicki.notesmanager.dto.GrantResult;
import pl.betacom.plecicki.notesmanager.dto.ShareItemRequest;
import pl.betacom.plecicki.notesmanager.entity.Item;
import pl.betacom.plecicki.notesmanager.entity.ItemPermission;
import pl.betacom.plecicki.notesmanager.entity.Role;
import pl.betacom.plecicki.notesmanager.entity.User;
import pl.betacom.plecicki.notesmanager.exception.InvalidShareRoleException;
import pl.betacom.plecicki.notesmanager.exception.ItemAccessDeniedException;
import pl.betacom.plecicki.notesmanager.exception.PermissionNotFoundException;
import pl.betacom.plecicki.notesmanager.exception.SelfShareException;
import pl.betacom.plecicki.notesmanager.exception.UserNotFoundException;
import pl.betacom.plecicki.notesmanager.repository.ItemPermissionRepository;
import pl.betacom.plecicki.notesmanager.repository.ItemRepository;
import pl.betacom.plecicki.notesmanager.repository.UserRepository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ItemPermissionServiceTest {

    @Mock
    private ItemRepository itemRepository;
    @Mock
    private ItemPermissionRepository itemPermissionRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ItemAccessService itemAccessService;

    @InjectMocks
    private ItemPermissionService itemPermissionService;

    private final UUID itemId = UUID.randomUUID();
    private final UUID ownerId = UUID.randomUUID();
    private final UUID targetId = UUID.randomUUID();

    private User user(UUID id) {
        return User.builder().id(id).login("user-" + id).password("pw").build();
    }

    private Item item() {
        return Item.builder().id(itemId).owner(user(ownerId)).title("t").content("c").version(1).deleted(false).build();
    }

    @Test
    @DisplayName("grant should throw forbidden for non-owner")
    void grantShouldThrowForbiddenForNonOwner() {
        Item item = item();
        when(itemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(itemAccessService.isOwner(eq(item), any())).thenReturn(false);

        assertThatThrownBy(() -> itemPermissionService.grant(itemId, new ShareItemRequest(targetId, Role.VIEWER), user(targetId)))
                .isInstanceOf(ItemAccessDeniedException.class);

        verify(itemPermissionRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("grant should throw 400 when sharing with self")
    void grantShouldThrowForSelfShare() {
        Item item = item();
        when(itemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(itemAccessService.isOwner(eq(item), any())).thenReturn(true);

        assertThatThrownBy(() -> itemPermissionService.grant(itemId, new ShareItemRequest(ownerId, Role.VIEWER), user(ownerId)))
                .isInstanceOf(SelfShareException.class);
    }

    @Test
    @DisplayName("grant should reject OWNER role")
    void grantShouldRejectOwnerRole() {
        Item item = item();
        when(itemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(itemAccessService.isOwner(eq(item), any())).thenReturn(true);

        assertThatThrownBy(() -> itemPermissionService.grant(itemId, new ShareItemRequest(targetId, Role.OWNER), user(ownerId)))
                .isInstanceOf(InvalidShareRoleException.class);
    }

    @Test
    @DisplayName("grant should throw 404 for missing target user")
    void grantShouldThrowForMissingUser() {
        Item item = item();
        when(itemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(itemAccessService.isOwner(eq(item), any())).thenReturn(true);
        when(userRepository.findById(targetId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> itemPermissionService.grant(itemId, new ShareItemRequest(targetId, Role.VIEWER), user(ownerId)))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    @DisplayName("grant should create new permission with grantedAt")
    void grantShouldCreatePermission() {
        Item item = item();
        when(itemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(itemAccessService.isOwner(eq(item), any())).thenReturn(true);
        when(userRepository.findById(targetId)).thenReturn(Optional.of(user(targetId)));
        when(itemPermissionRepository.findByItemIdAndUserId(itemId, targetId)).thenReturn(Optional.empty());
        ItemPermission saved = ItemPermission.builder().item(item).user(user(targetId)).role(Role.VIEWER).grantedAt(Instant.now()).build();
        when(itemPermissionRepository.saveAndFlush(any(ItemPermission.class))).thenReturn(saved);

        GrantResult result = itemPermissionService.grant(itemId, new ShareItemRequest(targetId, Role.VIEWER), user(ownerId));

        assertThat(result.created()).isTrue();
        assertThat(result.permission().userId()).isEqualTo(targetId);
        assertThat(result.permission().role()).isEqualTo(Role.VIEWER);
        assertThat(result.permission().grantedAt()).isNotNull();

        ArgumentCaptor<ItemPermission> captor = ArgumentCaptor.forClass(ItemPermission.class);
        verify(itemPermissionRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getRole()).isEqualTo(Role.VIEWER);
        assertThat(captor.getValue().getGrantedAt()).isNotNull();
    }

    @Test
    @DisplayName("grant should overwrite existing role and keep grantedAt")
    void grantShouldOverwriteRole() {
        Item item = item();
        when(itemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(itemAccessService.isOwner(eq(item), any())).thenReturn(true);
        when(userRepository.findById(targetId)).thenReturn(Optional.of(user(targetId)));

        Instant originalGrant = Instant.now().minusSeconds(60);
        ItemPermission existing = ItemPermission.builder().item(item).user(user(targetId)).role(Role.VIEWER).grantedAt(originalGrant).build();
        when(itemPermissionRepository.findByItemIdAndUserId(itemId, targetId)).thenReturn(Optional.of(existing));
        when(itemPermissionRepository.saveAndFlush(any(ItemPermission.class))).thenAnswer(inv -> inv.getArgument(0));

        GrantResult result = itemPermissionService.grant(itemId, new ShareItemRequest(targetId, Role.EDITOR), user(ownerId));

        assertThat(result.created()).isFalse();
        assertThat(existing.getRole()).isEqualTo(Role.EDITOR);
        assertThat(result.permission().grantedAt()).isEqualTo(originalGrant);
        verify(itemPermissionRepository).saveAndFlush(existing);
    }

    @Test
    @DisplayName("revoke should delete permission")
    void revokeShouldDeletePermission() {
        Item item = item();
        when(itemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(itemAccessService.isOwner(eq(item), any())).thenReturn(true);
        when(itemPermissionRepository.deleteByItemIdAndUserId(itemId, targetId)).thenReturn(1L);

        itemPermissionService.revoke(itemId, targetId, user(ownerId));

        verify(itemPermissionRepository).deleteByItemIdAndUserId(itemId, targetId);
    }

    @Test
    @DisplayName("revoke should throw 404 when no permission")
    void revokeShouldThrowNotFound() {
        Item item = item();
        when(itemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(itemAccessService.isOwner(eq(item), any())).thenReturn(true);
        when(itemPermissionRepository.deleteByItemIdAndUserId(itemId, targetId)).thenReturn(0L);

        assertThatThrownBy(() -> itemPermissionService.revoke(itemId, targetId, user(ownerId)))
                .isInstanceOf(PermissionNotFoundException.class);
    }

    @Test
    @DisplayName("revoke should throw forbidden for non-owner")
    void revokeShouldThrowForbidden() {
        Item item = item();
        when(itemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(itemAccessService.isOwner(eq(item), any())).thenReturn(false);

        assertThatThrownBy(() -> itemPermissionService.revoke(itemId, targetId, user(targetId)))
                .isInstanceOf(ItemAccessDeniedException.class);

        verify(itemPermissionRepository, never()).deleteByItemIdAndUserId(any(), any());
    }
}