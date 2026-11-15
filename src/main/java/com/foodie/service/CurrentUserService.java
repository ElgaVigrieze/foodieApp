package com.foodie.service;

import com.foodie.exception.UnauthenticatedException;
import com.foodie.exception.ForbiddenException;
import com.foodie.model.db.AppUser;
import com.foodie.model.db.Household;
import com.foodie.model.OwnedEntity;
import com.foodie.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
@Slf4j
public class CurrentUserService {

    private final AppUserRepository appUserRepository;

    public AppUser getCurrentUser() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof OAuth2User oAuth2User) {
            String googleId = oAuth2User.getAttribute("sub");
            // Database failures propagate (reported as a 500): "could not load the user" must not
            // be mistaken for "nobody is logged in".
            return appUserRepository.findByGoogleId(googleId).orElse(null);
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

    /**
     * The current user's household. Every logged-in user has one: {@code HouseholdRequiredInterceptor}
     * sends users without a household to the setup page before they reach anything else.
     *
     * @throws UnauthenticatedException if nobody is logged in
     * @throws ForbiddenException       if the user has not created or joined a household yet
     */
    public Household requireCurrentHousehold() {
        AppUser user = getCurrentUser();
        if (user == null) {
            throw new UnauthenticatedException("Not authenticated");
        }
        if (user.getHousehold() == null) {
            throw new ForbiddenException("Create or join a household first");
        }
        return user.getHousehold();
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
     * @throws ForbiddenException if not authenticated or not authorized
     */
    public void validateUserAccess(OwnedEntity entry) {
        AppUser currentUser = getCurrentUser();
        if (currentUser == null) {
            throw new UnauthenticatedException("Not authenticated");
        }

        boolean isOwner = entry.getOwner() != null &&
                entry.getOwner().getId().equals(currentUser.getId());
        boolean isSameHousehold = entry.getHousehold() != null &&
                currentUser.getHousehold() != null &&
                entry.getHousehold().getId().equals(currentUser.getHousehold().getId());

        if (!isOwner && !isSameHousehold) {
            throw new ForbiddenException("Not authorized to access this entry");
        }
    }
}
