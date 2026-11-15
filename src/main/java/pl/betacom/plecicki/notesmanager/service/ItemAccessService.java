package pl.betacom.plecicki.notesmanager.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pl.betacom.plecicki.notesmanager.entity.Item;
import pl.betacom.plecicki.notesmanager.entity.Role;
import pl.betacom.plecicki.notesmanager.entity.User;
import pl.betacom.plecicki.notesmanager.repository.ItemPermissionRepository;

import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ItemAccessService {

    private final ItemPermissionRepository itemPermissionRepository;

    public boolean isOwner(Item item, User user) {
        if (item == null || user == null || item.getOwner() == null) {
            return false;
        }
        return Objects.equals(item.getOwner().getId(), user.getId());
    }

    public boolean canRead(Item item, User user) {
        return isOwner(item, user)
                || itemPermissionRepository.findByItemIdAndUserId(item.getId(), user.getId()).isPresent();
    }

    public boolean canEdit(Item item, User user) {
        if (isOwner(item, user)) {
            return true;
        }
        return itemPermissionRepository.findByItemIdAndUserId(item.getId(), user.getId())
                .map(permission -> permission.getRole() == Role.EDITOR)
                .orElse(false);
    }
}