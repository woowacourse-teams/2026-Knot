package com.knot.backend.auth.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.knot.backend.auth.application.AuthService;
import com.knot.backend.auth.application.DeviceAuthService;
import com.knot.backend.auth.application.dto.command.CompleteNicknameCommand;
import com.knot.backend.auth.domain.AuthenticatedMember;
import com.knot.backend.auth.presentation.dto.request.CompleteNicknameRequest;
import com.knot.backend.auth.presentation.dto.response.AccessTokenResponse;
import com.knot.backend.auth.presentation.dto.response.AuthenticatedMemberResponse;
import com.knot.backend.global.config.JwtProperties;
import java.time.Duration;
import org.springframework.http.ResponseEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AuthControllerTest {

    @Test
    @DisplayName("인증된 member 정보를 응답 DTO로 반환한다")
    void me_success() {
        // given
        AuthController controller = new AuthController(
                mock(AuthService.class),
                mock(DeviceAuthService.class),
                new JwtProperties()
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
    @DisplayName("닉네임 설정 요청이 성공하면 access token을 응답 본문으로 돌려준다")
    void completeNicknameSetup_success() {
        // given
        AuthService authService = mock(AuthService.class);
        JwtProperties jwtProperties = new JwtProperties();
        jwtProperties.setExpiration(Duration.ofHours(1));
        AuthController controller = new AuthController(
                authService,
                mock(DeviceAuthService.class),
                jwtProperties
        );
        when(
                authService.completeNicknameSetup(
                        new CompleteNicknameCommand(
                                "onboarding-token",
                                "octocat"
                        )
                )
        ).thenReturn("access-token");

        // when
        AccessTokenResponse result = controller.completeNicknameSetup(
                "Bearer onboarding-token",
                new CompleteNicknameRequest("octocat")
        );

        // then
        assertThat(result.accessToken()).isEqualTo("access-token");
        assertThat(result.tokenType()).isEqualTo("Bearer");
        assertThat(result.expiresIn()).isEqualTo(3600L);
    }

    @Test
    @DisplayName("Bearer 스킴이 아닌 Authorization 헤더는 온보딩 토큰으로 넘기지 않는다")
    void completeNicknameSetup_failure_missingBearerScheme() {
        // given
        AuthService authService = mock(AuthService.class);
        JwtProperties jwtProperties = new JwtProperties();
        jwtProperties.setExpiration(Duration.ofHours(1));
        AuthController controller = new AuthController(
                authService,
                mock(DeviceAuthService.class),
                jwtProperties
        );
        when(
                authService.completeNicknameSetup(
                        new CompleteNicknameCommand(
                                null,
                                "octocat"
                        )
                )
        ).thenReturn("access-token");

        // when
        AccessTokenResponse result = controller.completeNicknameSetup(
                "onboarding-token",
                new CompleteNicknameRequest("octocat")
        );

        // then
        assertThat(result.accessToken()).isEqualTo("access-token");
    }

    @Test
    @DisplayName("로그아웃은 본문 없이 204를 돌려준다")
    void logout_success() {
        // given
        DeviceAuthService deviceAuthService = mock(DeviceAuthService.class);
        AuthController controller = new AuthController(
                mock(AuthService.class),
                deviceAuthService,
                new JwtProperties()
        );

        // when
        ResponseEntity<Void> result = controller.logout(
                AuthenticatedMember.of(
                        1L,
                        "octocat",
                        null
                )
        );

        // then
        assertThat(
                result.getStatusCode()
                        .value()
        ).isEqualTo(204);
        assertThat(result.getBody()).isNull();
        verify(
                deviceAuthService,
                never()
        ).revokeSession(
                anyLong(),
                anyLong()
        );
    }

    @Test
    @DisplayName("인증 주체가 없어도 로그아웃은 204를 돌려준다")
    void logout_success_withoutPrincipal() {
        // given
        AuthController controller = new AuthController(
                mock(AuthService.class),
                mock(DeviceAuthService.class),
                new JwtProperties()
        );

        // when
        ResponseEntity<Void> result = controller.logout(null);

        // then
        assertThat(
                result.getStatusCode()
                        .value()
        ).isEqualTo(204);
    }

    @Test
    @DisplayName("데스크톱 디바이스 토큰으로 로그아웃하면 그 기기 세션을 폐기한다")
    void logout_success_revokesDeviceSession() {
        // given
        DeviceAuthService deviceAuthService = mock(DeviceAuthService.class);
        AuthController controller = new AuthController(
                mock(AuthService.class),
                deviceAuthService,
                new JwtProperties()
        );

        // when
        ResponseEntity<Void> result = controller.logout(
                AuthenticatedMember.ofDeviceSession(
                        1L,
                        "octocat",
                        null,
                        77L
                )
        );

        // then
        assertThat(
                result.getStatusCode()
                        .value()
        ).isEqualTo(204);
        verify(deviceAuthService).revokeSession(
                77L,
                1L
        );
    }
}
