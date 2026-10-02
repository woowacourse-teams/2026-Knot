package com.knot.backend.global.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.knot.backend.global.response.ErrorResponse;
import com.knot.backend.workspace.domain.WorkspaceErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class GlobalExceptionHandlerTest {

    @DisplayName("호출 제한 예외는 429와 Retry-After 오류 응답으로 변환한다")
    @Test
    void handleTooManyRequestsException_success() {
        // given
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        RateLimitedProjectException exception = new RateLimitedProjectException(47);

        // when
        ResponseEntity<ErrorResponse> response = handler.handleProjectException(exception);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(
                response.getHeaders()
                        .getFirst(HttpHeaders.RETRY_AFTER)
        ).isEqualTo("47");
        assertThat(response.getBody()).isNotNull();
        assertThat(
                response.getBody()
                        .code()
        ).isEqualTo("WORKSPACE_INVITATION_PREVIEW_RATE_LIMIT_EXCEEDED");
        assertThat(
                response.getBody()
                        .message()
        ).isEqualTo("워크스페이스 초대 코드 조회 요청이 너무 많습니다");
    }

    private static final class RateLimitedProjectException extends ProjectException implements RetryAfterException {
        private final long retryAfterSeconds;

        private RateLimitedProjectException(long retryAfterSeconds) {
            super(WorkspaceErrorCode.WORKSPACE_INVITATION_PREVIEW_RATE_LIMIT_EXCEEDED);
            this.retryAfterSeconds = retryAfterSeconds;
        }

        @Override
        public long getRetryAfterSeconds() {
            return retryAfterSeconds;
        }
    }
}
