package pl.betacom.plecicki.notesmanager.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.betacom.plecicki.notesmanager.dto.CreateItemRequest;
import pl.betacom.plecicki.notesmanager.dto.ItemPatchResponse;
import pl.betacom.plecicki.notesmanager.dto.ItemResponse;
import pl.betacom.plecicki.notesmanager.dto.ItemSummaryResponse;
import pl.betacom.plecicki.notesmanager.dto.UpdateItemRequest;
import pl.betacom.plecicki.notesmanager.entity.Item;
import pl.betacom.plecicki.notesmanager.entity.ItemPermission;
import pl.betacom.plecicki.notesmanager.entity.Role;
import pl.betacom.plecicki.notesmanager.entity.User;
import pl.betacom.plecicki.notesmanager.exception.ItemAccessDeniedException;
import pl.betacom.plecicki.notesmanager.exception.ItemNotFoundException;
import pl.betacom.plecicki.notesmanager.exception.ItemVersionConflictException;
import pl.betacom.plecicki.notesmanager.repository.ItemPermissionRepository;
import pl.betacom.plecicki.notesmanager.repository.ItemRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ItemServiceTest {

    @Mock
    private ItemRepository itemRepository;
    @Mock
    private ItemPermissionRepository itemPermissionRepository;
    @Mock
    private ItemAccessService itemAccessService;

    @InjectMocks
    private ItemService itemService;

    private final UUID itemId = UUID.randomUUID();
    private final UUID ownerId = UUID.randomUUID();
    private final UUID viewerId = UUID.randomUUID();
    private final Instant now = Instant.now();

    private User user(UUID id) {
        return User.builder().id(id).login("user-" + id).password("pw").build();
    }

    private Item item(int version) {
        return Item.builder()
                .id(itemId)
                .owner(user(ownerId))
                .title("title")
                .content("content")
                .version(version)
                .deleted(false)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    @Test
    @DisplayName("create should set owner and trim title")
    void createShouldSetOwnerAndTrimTitle() {
        Item saved = item(0);
        when(itemRepository.saveAndFlush(any(Item.class))).thenReturn(saved);

        ItemResponse response = itemService.create(new CreateItemRequest("  Title  ", "content"), user(ownerId));

        ArgumentCaptor<Item> captor = ArgumentCaptor.forClass(Item.class);
        verify(itemRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getTitle()).isEqualTo("Title");
        assertThat(captor.getValue().getOwner().getId()).isEqualTo(ownerId);
        assertThat(response.id()).isEqualTo(itemId);
        assertThat(response.version()).isEqualTo(0);
        assertThat(response.ownerId()).isEqualTo(ownerId);
    }

    @Test
    @DisplayName("findAll should assign OWNER role for owned and permission role for shared items")
    void findAllShouldAssignRoles() {
        UUID otherOwner = UUID.randomUUID();
        Item owned = item(1);
        Item shared = Item.builder()
                .id(UUID.randomUUID())
                .owner(user(otherOwner))
                .title("shared")
                .content("c")
                .version(2)
                .deleted(false)
                .createdAt(now)
                .updatedAt(now)
                .build();

        when(itemRepository.findAllAccessible(ownerId)).thenReturn(List.of(owned, shared));
        when(itemPermissionRepository.findByUserId(ownerId)).thenReturn(List.of(
                ItemPermission.builder().item(shared).user(user(ownerId)).role(Role.VIEWER).build()));

        when(itemAccessService.isOwner(eq(owned), any())).thenReturn(true);
        when(itemAccessService.isOwner(eq(shared), any())).thenReturn(false);

        List<ItemSummaryResponse> result = itemService.findAll(user(ownerId));

        assertThat(result).hasSize(2);
        assertThat(result.get(0).myRole()).isEqualTo(Role.OWNER);
        assertThat(result.get(1).myRole()).isEqualTo(Role.VIEWER);
    }

    @Test
    @DisplayName("update should modify fields and return response")
    void updateShouldModifyFields() {
        Item item = item(2);
        when(itemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(itemAccessService.canEdit(eq(item), any())).thenReturn(true);
        when(itemRepository.saveAndFlush(any(Item.class))).thenAnswer(inv -> inv.getArgument(0));

        ItemPatchResponse response = itemService.update(itemId, new UpdateItemRequest("  new title  ", "new content", 2), user(ownerId));

        assertThat(item.getTitle()).isEqualTo("new title");
        assertThat(item.getContent()).isEqualTo("new content");
        assertThat(response.title()).isEqualTo("new title");
        assertThat(response.version()).isEqualTo(2);
    }

    @Test
    @DisplayName("update should leave content unchanged when null")
    void updateShouldLeaveContentWhenNull() {
        Item item = item(2);
        when(itemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(itemAccessService.canEdit(eq(item), any())).thenReturn(true);
        when(itemRepository.saveAndFlush(any(Item.class))).thenAnswer(inv -> inv.getArgument(0));

        itemService.update(itemId, new UpdateItemRequest("new title", null, 2), user(ownerId));

        assertThat(item.getTitle()).isEqualTo("new title");
        assertThat(item.getContent()).isEqualTo("content");
    }

    @Test
    @DisplayName("update should throw forbidden when user cannot edit")
    void updateShouldThrowForbidden() {
        Item item = item(2);
        when(itemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(itemAccessService.canEdit(eq(item), any())).thenReturn(false);

        assertThatThrownBy(() -> itemService.update(itemId, new UpdateItemRequest("new", null, 2), user(viewerId)))
                .isInstanceOf(ItemAccessDeniedException.class);

        verify(itemRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("update should throw version conflict with current version")
    void updateShouldThrowVersionConflict() {
        Item item = item(2);
        when(itemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(itemAccessService.canEdit(eq(item), any())).thenReturn(true);

        ItemVersionConflictException ex = catchThrowableOfType(
                () -> itemService.update(itemId, new UpdateItemRequest("new", null, 1), user(ownerId)),
                ItemVersionConflictException.class);

        assertThat(ex.getCurrentVersion()).isEqualTo(2);
        verify(itemRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("update should throw not found for missing item")
    void updateShouldThrowNotFound() {
        when(itemRepository.findById(itemId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> itemService.update(itemId, new UpdateItemRequest("x", null, 1), user(ownerId)))
                .isInstanceOf(ItemNotFoundException.class);
    }

    @Test
    @DisplayName("update should throw not found for deleted item")
    void updateShouldThrowNotFoundForDeleted() {
        Item deleted = item(1);
        deleted.setDeleted(true);
        when(itemRepository.findById(itemId)).thenReturn(Optional.of(deleted));

        assertThatThrownBy(() -> itemService.update(itemId, new UpdateItemRequest("x", null, 1), user(ownerId)))
                .isInstanceOf(ItemNotFoundException.class);
    }

    @Test
    @DisplayName("softDelete should mark item deleted for owner")
    void softDeleteShouldMarkDeleted() {
        Item item = item(1);
        when(itemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(itemAccessService.isOwner(eq(item), any())).thenReturn(true);

        itemService.softDelete(itemId, user(ownerId));

        assertThat(item.isDeleted()).isTrue();
        verify(itemRepository).saveAndFlush(item);
    }

    @Test
    @DisplayName("softDelete should throw forbidden for non-owner")
    void softDeleteShouldThrowForbidden() {
        Item item = item(1);
        when(itemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(itemAccessService.isOwner(eq(item), any())).thenReturn(false);

        assertThatThrownBy(() -> itemService.softDelete(itemId, user(viewerId)))
                .isInstanceOf(ItemAccessDeniedException.class);

        verify(itemRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("update should leave title unchanged when null")
    void updateShouldLeaveTitleWhenNull() {
        Item item = item(2);
        when(itemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(itemAccessService.canEdit(eq(item), any())).thenReturn(true);
        when(itemRepository.saveAndFlush(any(Item.class))).thenAnswer(inv -> inv.getArgument(0));

        itemService.update(itemId, new UpdateItemRequest(null, "new content", 2), user(ownerId));

        assertThat(item.getTitle()).isEqualTo("title");
        assertThat(item.getContent()).isEqualTo("new content");
    }

    @Test
    @DisplayName("softDelete should throw not found for deleted item")
    void softDeleteShouldThrowNotFoundForDeleted() {
        Item deleted = item(1);
        deleted.setDeleted(true);
        when(itemRepository.findById(itemId)).thenReturn(Optional.of(deleted));

        assertThatThrownBy(() -> itemService.softDelete(itemId, user(ownerId)))
                .isInstanceOf(ItemNotFoundException.class);
    }
}