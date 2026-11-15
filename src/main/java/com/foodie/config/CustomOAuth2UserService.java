package com.foodie.config;

import com.foodie.model.db.AppUser;
import com.foodie.model.HouseholdRole;
import com.foodie.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.dao.DataAccessException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class CustomOAuth2UserService extends OidcUserService {

    private final AppUserRepository appUserRepository;

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        OidcUser oAuth2User = super.loadUser(userRequest);

        String googleId = oAuth2User.getAttribute("sub");
        String email = oAuth2User.getAttribute("email");
        String name = oAuth2User.getAttribute("name");
        String picture = oAuth2User.getAttribute("picture");

        try {
            appUserRepository.findByGoogleId(googleId).ifPresentOrElse(
                    existingUser -> {
                        existingUser.setEmail(email);
                        existingUser.setName(name);
                        existingUser.setPictureUrl(picture);
                        appUserRepository.save(existingUser);
                        log.info("Updated user: {}", email);
                    },
                    () -> {
                        AppUser newUser = new AppUser();
                        newUser.setGoogleId(googleId);
                        newUser.setEmail(email);
                        newUser.setName(name);
                        newUser.setPictureUrl(picture);
                        newUser.setHouseholdRole(HouseholdRole.MEMBER);
                        appUserRepository.save(newUser);
                        log.info("Created new user: {}", email);
                    }
            );
        } catch (DataAccessException e) {
            // Refuse the login: continuing would leave a session with no AppUser behind it.
            log.error("Failed to save/update user {}: {}", email, e.getMessage(), e);
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("user_save_failed", "Could not complete login. Please try again.", null), e);
        }

        return oAuth2User;
    }
}