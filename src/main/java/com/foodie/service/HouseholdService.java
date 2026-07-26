package com.foodie.service;

import com.foodie.model.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import com.foodie.repository.AppUserRepository;
import com.foodie.repository.HouseholdRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class HouseholdService {

    private final HouseholdRepository householdRepository;
    private final AppUserRepository appUserRepository;
    private final CurrentUserService currentUserService;

    @Transactional
    public Household createHousehold(String name) {
        // Get user directly from OAuth principal to avoid entity loading issues
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof OAuth2User oAuth2User)) {
            throw new IllegalStateException("Must be logged in to create a household");
        }
        String googleId = oAuth2User.getAttribute("sub");
        AppUser user = appUserRepository.findByGoogleId(googleId)
                .orElseThrow(() -> new IllegalStateException("User not found in DB"));
        Household household = Household.builder().name(name).build();
        household = householdRepository.save(household);
        user.setHousehold(household);
        user.setHouseholdRole(HouseholdRole.OWNER);
        appUserRepository.save(user);
        return household;
    }

    @Transactional
    public boolean joinHousehold(String inviteCode) {
        AppUser user = currentUserService.getCurrentUser();
        return householdRepository.findByInviteCode(inviteCode.trim().toUpperCase())
                .map(household -> {
                    user.setHousehold(household);
                    user.setHouseholdRole(HouseholdRole.MEMBER);
                    appUserRepository.save(user);
                    return true;
                }).orElse(false);
    }

    public Household getCurrentHousehold() {
        AppUser user = currentUserService.getCurrentUser();
        return user != null ? user.getHousehold() : null;
    }

    public boolean currentUserIsOwner() {
        AppUser user = currentUserService.getCurrentUser();
        return user != null && user.isOwner();
    }
}
