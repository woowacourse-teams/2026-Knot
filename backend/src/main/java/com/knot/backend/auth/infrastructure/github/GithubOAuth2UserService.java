package com.knot.backend.auth.infrastructure.github;

import com.knot.backend.auth.domain.AuthException;
import com.knot.backend.auth.domain.OAuthUser;
import java.util.Map;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

@Service
public class GithubOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    private final OAuth2UserService<OAuth2UserRequest, OAuth2User> delegate;

    public GithubOAuth2UserService() {
        this(new DefaultOAuth2UserService());
    }

    GithubOAuth2UserService(OAuth2UserService<OAuth2UserRequest, OAuth2User> delegate) {
        this.delegate = delegate;
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) {
        if (!"github".equals(
                userRequest.getClientRegistration()
                        .getRegistrationId()
        )) {
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("unsupported_provider"),
                    "Only GitHub login is supported"
            );
        }

        try {
            OAuth2User user = delegate.loadUser(userRequest);
            OAuthUser oauthUser = toOAuthUser(user.getAttributes());
            return GithubOAuth2User.of(
                    oauthUser,
                    user
            );
        } catch (OAuth2AuthenticationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("github_user_info_unavailable"),
                    "GitHub 사용자 정보를 조회하지 못했습니다",
                    exception
            );
        }
    }

    private OAuthUser toOAuthUser(Map<String, ?> attributes) {
        try {
            GithubUserAttributes githubAttributes = GithubUserAttributes.from(attributes);
            return OAuthUser.of(
                    githubAttributes.id(),
                    githubAttributes.login(),
                    githubAttributes.avatarUrl()
            );
        } catch (AuthException exception) {
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("invalid_user_info"),
                    "GitHub user information is invalid",
                    exception
            );
        }
    }
}
