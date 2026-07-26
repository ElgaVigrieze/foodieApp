package com.foodie.service;

import com.foodie.model.AppUser;
import com.foodie.model.Household;
import com.foodie.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

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

    public boolean hasHousehold() {
        return getCurrentHousehold() != null;
    }
}