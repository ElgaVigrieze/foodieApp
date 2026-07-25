package com.foodie.config;

import com.foodie.model.AppUser;
import com.foodie.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

/**
 * On successful OAuth2 login, creates or updates the AppUser in our DB.
 */
@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final AppUserRepository appUserRepository;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);

        String googleId = oAuth2User.getAttribute("sub");
        String email = oAuth2User.getAttribute("email");
        String name = oAuth2User.getAttribute("name");
        String picture = oAuth2User.getAttribute("picture");

        // Create or update user
        appUserRepository.findByGoogleId(googleId).ifPresentOrElse(
                existingUser -> {
                    existingUser.setEmail(email);
                    existingUser.setName(name);
                    existingUser.setPictureUrl(picture);
                    appUserRepository.save(existingUser);
                },
                () -> appUserRepository.save(AppUser.builder()
                        .googleId(googleId)
                        .email(email)
                        .name(name)
                        .pictureUrl(picture)
                        .build())
        );

        return oAuth2User;
    }
}
