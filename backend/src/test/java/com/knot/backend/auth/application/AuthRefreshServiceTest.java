package com.knot.backend.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.knot.backend.auth.application.dto.result.AuthRefreshResult;
import com.knot.backend.auth.domain.AuthErrorCode;
import com.knot.backend.auth.domain.AuthException;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AuthRefreshServiceTest {
    private final AuthSessionRefreshService sessionRefreshService = mock(AuthSessionRefreshService.class);
    private final AuthRefreshService service = new AuthRefreshService(sessionRefreshService);

    @Test
    @DisplayName("회전 결과를 refresh API 결과로 반환한다")
    void refresh_success() {
        // given
        AuthRefreshResult result = new AuthRefreshResult(
                "access-token",
                "refresh-token",
                Duration.ofDays(7)
        );
        when(sessionRefreshService.refresh("current-refresh-token")).thenReturn(Optional.of(result));

        // when
        AuthRefreshResult actual = service.refresh("current-refresh-token");

        // then
        assertThat(actual).isEqualTo(result);
        verify(sessionRefreshService).refresh("current-refresh-token");
    }

    @Test
    @DisplayName("재사용된 refresh 토큰 회전 결과는 트랜잭션 뒤 인증 오류로 변환한다")
    void refresh_failure_replayedToken() {
        // given
        when(sessionRefreshService.refresh("consumed-refresh-token")).thenReturn(Optional.empty());

        // when
        Throwable thrown = catchThrowable(() -> service.refresh("consumed-refresh-token"));

        // then
        assertThat(thrown).isInstanceOfSatisfying(
                AuthException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.UNAUTHENTICATED)
        );
        verify(sessionRefreshService).refresh("consumed-refresh-token");
    }
}
