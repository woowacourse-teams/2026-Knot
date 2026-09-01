package com.knot.backend.auth.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

import com.knot.backend.auth.application.AuthService;
import com.knot.backend.auth.application.dto.command.CompleteNicknameCommand;
import com.knot.backend.auth.domain.AuthenticatedMember;
import com.knot.backend.auth.presentation.dto.request.CompleteNicknameRequest;
import com.knot.backend.auth.presentation.dto.response.AuthenticatedMemberResponse;
import com.knot.backend.global.config.JwtProperties;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AuthControllerTest {

    @Test
    @DisplayName("인증된 member 정보를 응답 DTO로 반환한다")
    void me_success() {
        // given
        AuthController controller = new AuthController(
                mock(AuthService.class),
                new AuthCookieManager(new JwtProperties())
        );
        AuthenticatedMember member = AuthenticatedMember.of(
                1L,
                "octocat",
                "https://example.com/avatar"
        );

        // when
        AuthenticatedMemberResponse result = controller.me(member);

        // then
        assertThat(result.memberId()).isEqualTo(1L);
        assertThat(result.nickname()).isEqualTo("octocat");
    }

    @Test
    @DisplayName("닉네임 설정 요청이 성공하면 access token을 발급하고 닉네임 쿠키를 만료시킨다")
    void completeNicknameSetup_success() {
        // given
        AuthService authService = mock(AuthService.class);
        JwtProperties jwtProperties = new JwtProperties();
        jwtProperties.setCookieName("KNOT_ACCESS_TOKEN");
        jwtProperties.setNicknameCookieName("KNOT_NICKNAME_TOKEN");
        jwtProperties.setExpiration(Duration.ofHours(1));
        jwtProperties.setSecure(false);
        AuthController controller = new AuthController(
                authService,
                new AuthCookieManager(jwtProperties)
        );
        when(
                authService.completeNicknameSetup(
                        new CompleteNicknameCommand(
                                "nickname-token",
                                "octocat"
                        )
                )
        ).thenReturn("access-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        ResponseEntity<Void> result = controller.completeNicknameSetup(
                "nickname-token",
                new CompleteNicknameRequest("octocat"),
                response
        );

        // then
        assertThat(
                result.getStatusCode()
                        .value()
        ).isEqualTo(204);
        assertThat(
                response.getCookie("KNOT_ACCESS_TOKEN")
                        .getValue()
        ).isEqualTo("access-token");
        Cookie nicknameCookie = response.getCookie("KNOT_NICKNAME_TOKEN");
        assertThat(nicknameCookie).isNotNull();
        assertThat(nicknameCookie.getMaxAge()).isZero();
    }
}
