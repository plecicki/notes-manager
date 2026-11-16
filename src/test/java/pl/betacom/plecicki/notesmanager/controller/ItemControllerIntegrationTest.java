package pl.betacom.plecicki.notesmanager.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import pl.betacom.plecicki.notesmanager.config.SecurityConfig;
import pl.betacom.plecicki.notesmanager.dto.ItemPatchResponse;
import pl.betacom.plecicki.notesmanager.dto.ItemResponse;
import pl.betacom.plecicki.notesmanager.dto.GrantResult;
import pl.betacom.plecicki.notesmanager.dto.HistoryEntryResponse;
import pl.betacom.plecicki.notesmanager.dto.ItemSummaryResponse;
import pl.betacom.plecicki.notesmanager.dto.PermissionResponse;
import pl.betacom.plecicki.notesmanager.entity.Role;
import pl.betacom.plecicki.notesmanager.entity.User;
import pl.betacom.plecicki.notesmanager.exception.ItemAccessDeniedException;
import pl.betacom.plecicki.notesmanager.exception.InvalidShareRoleException;
import pl.betacom.plecicki.notesmanager.exception.ItemNotFoundException;
import pl.betacom.plecicki.notesmanager.exception.PermissionNotFoundException;
import pl.betacom.plecicki.notesmanager.exception.SelfShareException;
import pl.betacom.plecicki.notesmanager.exception.ItemVersionConflictException;
import pl.betacom.plecicki.notesmanager.repository.UserRepository;
import pl.betacom.plecicki.notesmanager.security.JwtAuthenticationFilter;
import pl.betacom.plecicki.notesmanager.security.JwtService;
import pl.betacom.plecicki.notesmanager.security.UserDetailsServiceImpl;
import pl.betacom.plecicki.notesmanager.service.CurrentUserService;
import pl.betacom.plecicki.notesmanager.service.ItemHistoryService;
import pl.betacom.plecicki.notesmanager.service.ItemPermissionService;
import pl.betacom.plecicki.notesmanager.service.ItemService;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ItemController.class)
@Import({SecurityConfig.class, JwtService.class, JwtAuthenticationFilter.class, UserDetailsServiceImpl.class, CurrentUserService.class})
class ItemControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockBean
    private ItemService itemService;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private ItemHistoryService itemHistoryService;

    @MockBean
    private ItemPermissionService itemPermissionService;

    private static final UUID ITEM_ID = UUID.fromString("7f3c2100-ab12-4fde-8765-112233445566");
    private static final UUID OWNER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");

    private String authenticate(String login) {
        User user = User.builder().id(OWNER_ID).login(login).password("encoded").build();
        when(userRepository.findByLogin(login)).thenReturn(Optional.of(user));
        return jwtService.generateToken(login);
    }

    @Test
    @DisplayName("POST /items with valid token returns 201")
    void createShouldReturnCreated() throws Exception {
        String token = authenticate("jan.kowalski");
        when(itemService.create(any(), any())).thenReturn(
                new ItemResponse(ITEM_ID, "title", "content", 0, OWNER_ID, Instant.now(), Instant.now()));

        mockMvc.perform(post("/items")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"title\",\"content\":\"content\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(ITEM_ID.toString()))
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.ownerId").value(OWNER_ID.toString()));
    }

    @Test
    @DisplayName("POST /items without token returns 401")
    void createShouldReturnUnauthorizedWithoutToken() throws Exception {
        mockMvc.perform(post("/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"title\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /items with blank title returns 400")
    void createShouldReturnBadRequestForBlankTitle() throws Exception {
        String token = authenticate("jan.kowalski");
        mockMvc.perform(post("/items")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.title").value("Field is required"));
    }

    @Test
    @DisplayName("GET /items returns list with myRole")
    void findAllShouldReturnList() throws Exception {
        String token = authenticate("jan.kowalski");
        when(itemService.findAll(any())).thenReturn(List.of(
                new ItemSummaryResponse(ITEM_ID, "title", "content", 1, OWNER_ID, Role.OWNER, Instant.now())));

        mockMvc.perform(get("/items").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].myRole").value("OWNER"))
                .andExpect(jsonPath("$[0].version").value(1));
    }

    @Test
    @DisplayName("PATCH /items/{id} returns 200 with new version")
    void updateShouldReturnOk() throws Exception {
        String token = authenticate("jan.kowalski");
        when(itemService.update(eq(ITEM_ID), any(), any())).thenReturn(
                new ItemPatchResponse(ITEM_ID, "new", "new content", 1, Instant.now()));

        mockMvc.perform(patch("/items/" + ITEM_ID)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"new\",\"version\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("new"))
                .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    @DisplayName("PATCH /items/{id} returns 403 for viewer")
    void updateShouldReturnForbidden() throws Exception {
        String token = authenticate("jan.kowalski");
        when(itemService.update(eq(ITEM_ID), any(), any())).thenThrow(new ItemAccessDeniedException());

        mockMvc.perform(patch("/items/" + ITEM_ID)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"new\",\"version\":0}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Forbidden"));
    }

    @Test
    @DisplayName("PATCH /items/{id} returns 404 for missing item")
    void updateShouldReturnNotFound() throws Exception {
        String token = authenticate("jan.kowalski");
        when(itemService.update(eq(ITEM_ID), any(), any())).thenThrow(new ItemNotFoundException(ITEM_ID));

        mockMvc.perform(patch("/items/" + ITEM_ID)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"new\",\"version\":0}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Not Found"));
    }

    @Test
    @DisplayName("PATCH /items/{id} returns 409 with currentVersion on conflict")
    void updateShouldReturnConflict() throws Exception {
        String token = authenticate("jan.kowalski");
        when(itemService.update(eq(ITEM_ID), any(), any())).thenThrow(new ItemVersionConflictException(5));

        mockMvc.perform(patch("/items/" + ITEM_ID)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"new\",\"version\":0}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.fields.currentVersion").value("5"));
    }

    @Test
    @DisplayName("PATCH /items/{id} without version returns 400")
    void updateShouldReturnBadRequestWithoutVersion() throws Exception {
        String token = authenticate("jan.kowalski");
        mockMvc.perform(patch("/items/" + ITEM_ID)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"new\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.version").value("Version is required"));
    }

    @Test
    @DisplayName("DELETE /items/{id} returns 204 for owner")
    void deleteShouldReturnNoContent() throws Exception {
        String token = authenticate("jan.kowalski");
        mockMvc.perform(delete("/items/" + ITEM_ID)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("DELETE /items/{id} returns 403 for non-owner")
    void deleteShouldReturnForbidden() throws Exception {
        String token = authenticate("jan.kowalski");
        doThrow(new ItemAccessDeniedException()).when(itemService).softDelete(eq(ITEM_ID), any());

        mockMvc.perform(delete("/items/" + ITEM_ID)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("DELETE /items/{id} returns 404 for missing item")
    void deleteShouldReturnNotFound() throws Exception {
        String token = authenticate("jan.kowalski");
        doThrow(new ItemNotFoundException(ITEM_ID)).when(itemService).softDelete(eq(ITEM_ID), any());

        mockMvc.perform(delete("/items/" + ITEM_ID)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /items with title longer than 255 chars returns 400")
    void createShouldReturnBadRequestForTooLongTitle() throws Exception {
        String token = authenticate("jan.kowalski");
        String longTitle = "a".repeat(256);

        mockMvc.perform(post("/items")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"" + longTitle + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.title").value("Title must be at most 255 characters"));
    }

    @Test
    @DisplayName("PATCH /items/{id} with blank title returns 400")
    void updateShouldReturnBadRequestForBlankTitle() throws Exception {
        String token = authenticate("jan.kowalski");

        mockMvc.perform(patch("/items/" + ITEM_ID)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"   \",\"version\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.title").value("Title must not be blank"));
    }

    @Test
    @DisplayName("PATCH /items/{id} with only content returns 200")
    void updateShouldReturnOkWithOnlyContent() throws Exception {
        String token = authenticate("jan.kowalski");
        when(itemService.update(eq(ITEM_ID), any(), any())).thenReturn(
                new ItemPatchResponse(ITEM_ID, "unchanged", "new content", 1, Instant.now()));

        mockMvc.perform(patch("/items/" + ITEM_ID)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"new content\",\"version\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("new content"));
    }

    @Test
    @DisplayName("GET /items/{id}/history returns 200")
    void historyShouldReturnOk() throws Exception {
        String token = authenticate("jan.kowalski");
        when(itemHistoryService.findHistory(eq(ITEM_ID), any())).thenReturn(List.of(
                new HistoryEntryResponse(1, "ADD", Instant.now(), "jan.kowalski", "title", "content")));

        mockMvc.perform(get("/items/" + ITEM_ID + "/history")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].revisionType").value("ADD"))
                .andExpect(jsonPath("$[0].changedBy").value("jan.kowalski"));
    }

    @Test
    @DisplayName("POST /items/{id}/share returns 201 for new grant")
    void shareShouldReturnCreated() throws Exception {
        String token = authenticate("jan.kowalski");
        when(itemPermissionService.grant(eq(ITEM_ID), any(), any())).thenReturn(
                new GrantResult(true, new PermissionResponse(ITEM_ID, OWNER_ID, Role.EDITOR, Instant.now())));

        mockMvc.perform(post("/items/" + ITEM_ID + "/share")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + OWNER_ID + "\",\"role\":\"EDITOR\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("EDITOR"));
    }

    @Test
    @DisplayName("POST /items/{id}/share returns 200 for role update")
    void shareShouldReturnOkForUpdate() throws Exception {
        String token = authenticate("jan.kowalski");
        when(itemPermissionService.grant(eq(ITEM_ID), any(), any())).thenReturn(
                new GrantResult(false, new PermissionResponse(ITEM_ID, OWNER_ID, Role.VIEWER, Instant.now())));

        mockMvc.perform(post("/items/" + ITEM_ID + "/share")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + OWNER_ID + "\",\"role\":\"VIEWER\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /items/{id}/share returns 400 for self-share")
    void shareShouldReturnBadRequestForSelf() throws Exception {
        String token = authenticate("jan.kowalski");
        when(itemPermissionService.grant(eq(ITEM_ID), any(), any())).thenThrow(new SelfShareException());

        mockMvc.perform(post("/items/" + ITEM_ID + "/share")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + OWNER_ID + "\",\"role\":\"VIEWER\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /items/{id}/share returns 403 for non-owner")
    void shareShouldReturnForbidden() throws Exception {
        String token = authenticate("jan.kowalski");
        when(itemPermissionService.grant(eq(ITEM_ID), any(), any())).thenThrow(new ItemAccessDeniedException());

        mockMvc.perform(post("/items/" + ITEM_ID + "/share")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + OWNER_ID + "\",\"role\":\"VIEWER\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("DELETE /items/{id}/share/{userId} returns 204")
    void revokeShouldReturnNoContent() throws Exception {
        String token = authenticate("jan.kowalski");
        mockMvc.perform(delete("/items/" + ITEM_ID + "/share/" + OWNER_ID)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("DELETE /items/{id}/share/{userId} returns 404 when no permission")
    void revokeShouldReturnNotFound() throws Exception {
        String token = authenticate("jan.kowalski");
        doThrow(new PermissionNotFoundException()).when(itemPermissionService).revoke(eq(ITEM_ID), eq(OWNER_ID), any());

        mockMvc.perform(delete("/items/" + ITEM_ID + "/share/" + OWNER_ID)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /items/{id}/share with OWNER role returns 400")
    void shareShouldReturnBadRequestForOwnerRole() throws Exception {
        String token = authenticate("jan.kowalski");
        when(itemPermissionService.grant(eq(ITEM_ID), any(), any())).thenThrow(new InvalidShareRoleException());

        mockMvc.perform(post("/items/" + ITEM_ID + "/share")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + OWNER_ID + "\",\"role\":\"OWNER\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /items/{id}/share with missing fields returns 400")
    void shareShouldReturnBadRequestForMissingFields() throws Exception {
        String token = authenticate("jan.kowalski");

        mockMvc.perform(post("/items/" + ITEM_ID + "/share")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.userId").value("Field is required"))
                .andExpect(jsonPath("$.fields.role").value("Field is required"));
    }

    @Test
    @DisplayName("POST /items/{id}/share returns 404 for missing item")
    void shareShouldReturnNotFound() throws Exception {
        String token = authenticate("jan.kowalski");
        when(itemPermissionService.grant(eq(ITEM_ID), any(), any())).thenThrow(new ItemNotFoundException(ITEM_ID));

        mockMvc.perform(post("/items/" + ITEM_ID + "/share")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + OWNER_ID + "\",\"role\":\"VIEWER\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("DELETE /items/{id}/share/{userId} returns 403 for non-owner")
    void revokeShouldReturnForbidden() throws Exception {
        String token = authenticate("jan.kowalski");
        doThrow(new ItemAccessDeniedException()).when(itemPermissionService).revoke(eq(ITEM_ID), eq(OWNER_ID), any());

        mockMvc.perform(delete("/items/" + ITEM_ID + "/share/" + OWNER_ID)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /items/{id}/history returns 403 without access")
    void historyShouldReturnForbidden() throws Exception {
        String token = authenticate("jan.kowalski");
        when(itemHistoryService.findHistory(eq(ITEM_ID), any())).thenThrow(new ItemAccessDeniedException());

        mockMvc.perform(get("/items/" + ITEM_ID + "/history")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /items/{id}/history returns 404 for missing item")
    void historyShouldReturnNotFound() throws Exception {
        String token = authenticate("jan.kowalski");
        when(itemHistoryService.findHistory(eq(ITEM_ID), any())).thenThrow(new ItemNotFoundException(ITEM_ID));

        mockMvc.perform(get("/items/" + ITEM_ID + "/history")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }
}