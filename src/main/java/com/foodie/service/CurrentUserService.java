package com.foodie.service;

import com.foodie.model.AppUser;
import com.foodie.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

/**
 * Helper to get the currently authenticated user.
 */
@Service
@RequiredArgsConstructor
public class CurrentUserService {

    private final AppUserRepository appUserRepository;

    /**
     * Get the current AppUser from the security context.
     */
    public AppUser getCurrentUser() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof OAuth2User oAuth2User) {
            String googleId = oAuth2User.getAttribute("sub");
            return appUserRepository.findByGoogleId(googleId).orElse(null);
        }
        return null;
    }
}
