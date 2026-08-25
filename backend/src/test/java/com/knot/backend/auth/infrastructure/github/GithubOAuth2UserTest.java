package com.knot.backend.auth.infrastructure.github;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import com.knot.backend.auth.domain.OAuthUser;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;

class GithubOAuth2UserTest {

    @Test
    @DisplayName("GitHub OAuth 사용자에 도메인 OAuth 정보를 보관한다")
    void create_success() {
        // given
        OAuthUser oauthUser = OAuthUser.of(
                42L,
                "octocat",
                "https://example.com/avatar"
        );
        OAuth2User delegate = mock(OAuth2User.class);
        GrantedAuthority authority = new SimpleGrantedAuthority("ROLE_USER");
        doReturn(List.of(authority)).when(delegate)
                .getAuthorities();
        doReturn(
                Map.of(
                        "id",
                        42L,
                        "login",
                        "octocat"
                )
        ).when(delegate)
                .getAttributes();

        // when
        GithubOAuth2User result = GithubOAuth2User.of(
                oauthUser,
                delegate
        );

        // then
        assertThat(result.getOAuthUser()).isEqualTo(oauthUser);
        assertThat(result.getName()).isEqualTo("42");
        assertThat(result.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_USER");
        assertThat(result.getAttributes()).containsEntry(
                "id",
                42L
        );
    }
}
