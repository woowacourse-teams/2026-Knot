package com.knot.backend.mcp.presentation;

import com.knot.backend.auth.domain.AuthenticatedMember;
import com.knot.backend.global.response.ErrorResponse;
import com.knot.backend.mcp.application.McpHttpResponse;
import com.knot.backend.mcp.application.McpProtocolVersions;
import com.knot.backend.mcp.application.McpRequestHandler;
import com.knot.backend.mcp.domain.McpErrorCode;
import com.knot.backend.mcp.domain.McpException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 원격 MCP 서버의 HTTP 표면(기획서 6.6, 로드맵 A13). {@code POST /mcp} 하나이며 인증은 기존
 * {@code Authorization: Bearer} 액세스 토큰(JwtAuthenticationFilter)으로 끝난다. 데스크톱 로컬 MCP 서버의 guard와 같은
 * 규칙으로 {@code Origin} 헤더가 있는 요청을 403으로 거부하고(로드맵 Q48·Q54), 지원하지 않는 {@code MCP-Protocol-Version}은
 * 400으로 거부한다. GET(서버 발신 SSE)·DELETE(세션 종료)는 무상태 서버라 405다.
 *
 * 기본값은 꺼짐이다(기획서 6.6) — 액세스 토큰 수명이 1시간이라 CLI 설정에 붙여 두기 어렵고, 일반 사용자용 경로는 데스크톱
 * 로컬 MCP 서버(기획서 6.4)다. {@code mcp.remote.enabled=true}로 켜야 엔드포인트가 생기고, 꺼진 빌드에서는 인증된 요청도 404다.
 */
@RestController
@RequestMapping("/mcp")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "mcp.remote", name = "enabled", havingValue = "true")
public class McpController implements McpApi {
    static final String PROTOCOL_VERSION_HEADER = "MCP-Protocol-Version";
    static final String METHOD_NOT_ALLOWED_CODE = "MCP_METHOD_NOT_ALLOWED";
    static final String METHOD_NOT_ALLOWED_MESSAGE = "MCP 엔드포인트는 POST만 받습니다";

    private final McpRequestHandler requestHandler;

    @Override
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Object> post(
            @RequestHeader(value = HttpHeaders.ORIGIN, required = false) String origin,
            @RequestHeader(value = PROTOCOL_VERSION_HEADER, required = false) String protocolVersion,
            @RequestBody(required = false) String body,
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember
    ) {
        if (origin != null) {
            throw new McpException(McpErrorCode.MCP_ORIGIN_NOT_ALLOWED);
        }
        if (protocolVersion != null && !McpProtocolVersions.isSupported(protocolVersion)) {
            throw new McpException(McpErrorCode.MCP_PROTOCOL_VERSION_UNSUPPORTED);
        }
        McpHttpResponse response = requestHandler.handle(
                body,
                authenticatedMember.getMemberId()
        );
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(response.status())
                .cacheControl(CacheControl.noStore());
        if (response.body() == null) {
            return builder.build();
        }
        return builder.contentType(MediaType.APPLICATION_JSON)
                .body(response.body());
    }

    @GetMapping
    public ResponseEntity<ErrorResponse> get() {
        return methodNotAllowed();
    }

    @DeleteMapping
    public ResponseEntity<ErrorResponse> delete() {
        return methodNotAllowed();
    }

    private static ResponseEntity<ErrorResponse> methodNotAllowed() {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .allow(HttpMethod.POST)
                .cacheControl(CacheControl.noStore())
                .contentType(MediaType.APPLICATION_JSON)
                .body(
                        new ErrorResponse(
                                METHOD_NOT_ALLOWED_CODE,
                                METHOD_NOT_ALLOWED_MESSAGE,
                                List.of()
                        )
                );
    }
}
