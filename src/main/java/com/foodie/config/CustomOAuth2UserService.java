package com.foodie.config;

import com.foodie.model.AppUser;
import com.foodie.model.HouseholdRole;
import com.foodie.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
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
        } catch (Exception e) {
            log.error("Failed to save/update user {}: {}", email, e.getMessage(), e);
        }

        return oAuth2User;
    }
}