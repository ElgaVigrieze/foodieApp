package com.foodie.service;

import com.foodie.model.db.AppUser;
import com.foodie.model.db.Household;
import com.foodie.model.OwnedEntity;
import com.foodie.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class CurrentUserService {

    private final AppUserRepository appUserRepository;

    public AppUser getCurrentUser() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof OAuth2User oAuth2User) {
            String googleId = oAuth2User.getAttribute("sub");
            try {
                return appUserRepository.findByGoogleId(googleId).orElse(null);
            } catch (Exception e) {
                System.err.println("WARN: Failed to load AppUser for " + googleId + ": " + e.getMessage());
                return null;
            }
        }
        return null;
    }

    public Household getCurrentHousehold() {
        AppUser user = getCurrentUser();
        return user != null ? user.getHousehold() : null;
    }

    public Long getCurrentHouseholdId() {
        Household h = getCurrentHousehold();
        return h != null ? h.getId() : null;
    }

    public boolean isOwner() {
        AppUser user = getCurrentUser();
        return user != null && user.isOwner();
    }

    @Transactional
    public void saveTargets(BigDecimal calories, BigDecimal fiber, BigDecimal carbs,
                            BigDecimal fat, BigDecimal protein, BigDecimal cost) {
        AppUser user = getCurrentUser();
        if (user == null) return;
        user.setTargetCalories(calories);
        user.setTargetFiber(fiber);
        user.setTargetCarbs(carbs);
        user.setTargetFat(fat);
        user.setTargetProtein(protein);
        user.setTargetCost(cost);
        appUserRepository.save(user);
    }

    /**
     * Validates that the current user has access to the given entity.
     * Access is granted if the user owns the entity OR is in the same household.
     * @throws SecurityException if not authenticated or not authorized
     */
    public void validateUserAccess(OwnedEntity entry) {
        AppUser currentUser = getCurrentUser();
        if (currentUser == null) {
            throw new SecurityException("Not authenticated");
        }

        boolean isOwner = entry.getOwner() != null &&
                entry.getOwner().getId().equals(currentUser.getId());
        boolean isSameHousehold = entry.getHousehold() != null &&
                currentUser.getHousehold() != null &&
                entry.getHousehold().getId().equals(currentUser.getHousehold().getId());

        if (!isOwner && !isSameHousehold) {
            throw new SecurityException("Not authorized to access this entry");
        }
    }
}