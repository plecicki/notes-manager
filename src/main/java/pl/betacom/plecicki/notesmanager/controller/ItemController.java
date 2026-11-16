package pl.betacom.plecicki.notesmanager.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.betacom.plecicki.notesmanager.dto.CreateItemRequest;
import pl.betacom.plecicki.notesmanager.dto.GrantResult;
import pl.betacom.plecicki.notesmanager.dto.HistoryEntryResponse;
import pl.betacom.plecicki.notesmanager.dto.ItemPatchResponse;
import pl.betacom.plecicki.notesmanager.dto.ItemResponse;
import pl.betacom.plecicki.notesmanager.dto.ItemSummaryResponse;
import pl.betacom.plecicki.notesmanager.dto.PermissionResponse;
import pl.betacom.plecicki.notesmanager.dto.ShareItemRequest;
import pl.betacom.plecicki.notesmanager.dto.UpdateItemRequest;
import pl.betacom.plecicki.notesmanager.service.CurrentUserService;
import pl.betacom.plecicki.notesmanager.service.ItemHistoryService;
import pl.betacom.plecicki.notesmanager.service.ItemPermissionService;
import pl.betacom.plecicki.notesmanager.service.ItemService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/items")
@RequiredArgsConstructor
public class ItemController {

    private final ItemService itemService;
    private final ItemHistoryService itemHistoryService;
    private final ItemPermissionService itemPermissionService;
    private final CurrentUserService currentUserService;

    @PostMapping
    public ResponseEntity<ItemResponse> create(@Valid @RequestBody CreateItemRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(itemService.create(request, currentUserService.getCurrentUser()));
    }

    @GetMapping
    public ResponseEntity<List<ItemSummaryResponse>> findAll() {
        return ResponseEntity.ok(itemService.findAll(currentUserService.getCurrentUser()));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ItemPatchResponse> update(@PathVariable UUID id,
                                                    @Valid @RequestBody UpdateItemRequest request) {
        return ResponseEntity.ok(itemService.update(id, request, currentUserService.getCurrentUser()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        itemService.softDelete(id, currentUserService.getCurrentUser());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/history")
    public ResponseEntity<List<HistoryEntryResponse>> history(@PathVariable UUID id) {
        return ResponseEntity.ok(itemHistoryService.findHistory(id, currentUserService.getCurrentUser()));
    }

    @PostMapping("/{id}/share")
    public ResponseEntity<PermissionResponse> share(@PathVariable UUID id,
                                                    @Valid @RequestBody ShareItemRequest request) {
        GrantResult result = itemPermissionService.grant(id, request, currentUserService.getCurrentUser());
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(result.permission());
    }

    @DeleteMapping("/{id}/share/{userId}")
    public ResponseEntity<Void> revoke(@PathVariable UUID id, @PathVariable UUID userId) {
        itemPermissionService.revoke(id, userId, currentUserService.getCurrentUser());
        return ResponseEntity.noContent().build();
    }
}