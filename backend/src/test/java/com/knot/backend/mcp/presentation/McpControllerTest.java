package com.knot.backend.mcp.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.knot.backend.auth.domain.AuthenticatedMember;
import com.knot.backend.global.response.ErrorResponse;
import com.knot.backend.mcp.application.McpHttpResponse;
import com.knot.backend.mcp.application.McpProtocolVersions;
import com.knot.backend.mcp.application.McpRequestHandler;
import com.knot.backend.mcp.domain.McpErrorCode;
import com.knot.backend.mcp.domain.McpException;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** 원격 MCP 서버의 HTTP 표면(기획서 6.6, 로드맵 A13). 브라우저 경로 차단·프로토콜 검사·허용 메서드. */
class McpControllerTest {
    private static final AuthenticatedMember MEMBER = AuthenticatedMember.of(
            7L,
            "건규",
            null
    );

    private final McpRequestHandler requestHandler = Mockito.mock(McpRequestHandler.class);
    private final McpController controller = new McpController(requestHandler);

    @Test
    @DisplayName("처리 결과를 그대로 싣고 캐시를 막는다")
    void post_success() {
        // given
        Mockito.when(
                requestHandler.handle(
                        "{}",
                        7L
                )
        )
                .thenReturn(
                        McpHttpResponse.ok(
                                Map.of(
                                        "ok",
                                        true
                                )
                        )
                );

        // when
        ResponseEntity<Object> response = controller.post(
                null,
                McpProtocolVersions.LATEST,
                "{}",
                MEMBER
        );

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(
                response.getHeaders()
                        .getCacheControl()
        ).isEqualTo("no-store");
        assertThat(response.getBody()).isEqualTo(
                Map.of(
                        "ok",
                        true
                )
        );
    }

    @Test
    @DisplayName("본문 없는 202 응답에는 본문을 싣지 않는다")
    void post_success_accepted() {
        // given
        Mockito.when(
                requestHandler.handle(
                        Mockito.anyString(),
                        Mockito.anyLong()
                )
        )
                .thenReturn(McpHttpResponse.accepted());

        // when
        ResponseEntity<Object> response = controller.post(
                null,
                null,
                "{}",
                MEMBER
        );

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody()).isNull();
    }

    @Test
    @DisplayName("Origin 헤더가 있으면 브라우저 경로로 보고 거부한다")
    void post_failure_originPresent() {
        // given

        // when & then
        assertThatThrownBy(
                () -> controller.post(
                        "https://knoted.kr",
                        null,
                        "{}",
                        MEMBER
                )
        )
                .isInstanceOfSatisfying(
                        McpException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(McpErrorCode.MCP_ORIGIN_NOT_ALLOWED)
                );
        Mockito.verifyNoInteractions(requestHandler);
    }

    @Test
    @DisplayName("지원하지 않는 MCP-Protocol-Version 헤더는 거부한다")
    void post_failure_unsupportedProtocolVersion() {
        // given

        // when & then
        assertThatThrownBy(
                () -> controller.post(
                        null,
                        "1999-01-01",
                        "{}",
                        MEMBER
                )
        )
                .isInstanceOfSatisfying(
                        McpException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(McpErrorCode.MCP_PROTOCOL_VERSION_UNSUPPORTED)
                );
        Mockito.verifyNoInteractions(requestHandler);
    }

    @Test
    @DisplayName("무상태 서버라 GET(서버 발신 SSE)·DELETE(세션 종료)는 405로 POST만 알린다")
    void getAndDelete_failure_methodNotAllowed() {
        // given

        // when
        ResponseEntity<ErrorResponse> get = controller.get();
        ResponseEntity<ErrorResponse> delete = controller.delete();

        // then
        assertThat(get.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(delete.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(
                get.getHeaders()
                        .getFirst(HttpHeaders.ALLOW)
        ).isEqualTo("POST");
        assertThat(get.getBody()
                .code()).isEqualTo(McpController.METHOD_NOT_ALLOWED_CODE);
    }
}
