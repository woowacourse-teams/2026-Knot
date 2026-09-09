package com.knot.backend.mcp.presentation;

import static com.knot.backend.global.config.OpenApiConfig.BEARER_AUTH;

import com.knot.backend.auth.domain.AuthenticatedMember;
import com.knot.backend.global.response.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

@Tag(name = "MCP", description = "원격 MCP 서버(개발자용). CLI 코딩 에이전트가 Streamable HTTP로 붙어 list_workspaces·search_documents 도구를 부른다. LLM을 부르지 않고 아무것도 저장하지 않는다")
@SecurityRequirement(name = BEARER_AUTH)
public interface McpApi {

    // @formatter:off
    @Operation(
            summary = "MCP JSON-RPC 엔드포인트",
            description = "MCP Streamable HTTP 단일 엔드포인트. JSON-RPC 2.0 메시지 하나를 받아 initialize·ping·tools/list·tools/call을 "
                    + "처리한다. 세션(Mcp-Session-Id)이 없는 무상태 서버이며 응답은 항상 application/json이다(SSE 스트림 없음). "
                    + "브라우저 요청(Origin 헤더 있음)은 거부한다. 등록 예: claude mcp add --transport http knot <서버>/mcp "
                    + "--header \"Authorization: Bearer <액세스 토큰>\""
    )
    @RequestBody(
            required = true,
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(
                            type = "object",
                            description = "JSON-RPC 2.0 요청·알림 하나. 배치(배열)는 받지 않는다",
                            example = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\",\"params\":{\"name\":\"search_documents\",\"arguments\":{\"query\":\"PostgreSQL을 왜 사용했나요?\",\"workspaceId\":1}}}"
                    )
            )
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "JSON-RPC 응답(result 또는 error)",
                    headers = @Header(
                            name = HttpHeaders.CACHE_CONTROL,
                            description = "사용자별 응답 캐시 방지",
                            schema = @Schema(type = "string", example = "no-store")
                    ),
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(
                                    type = "object",
                                    example = "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"content\":[{\"type\":\"text\",\"text\":\"...\"}],\"structuredContent\":{\"status\":\"READY\",\"chunks\":[]}}}"
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "202",
                    description = "알림(id 없음) 또는 클라이언트 응답을 받았음. 본문 없음"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "JSON 파싱 실패·JSON-RPC 형식 오류(본문은 JSON-RPC error) 또는 지원하지 않는 MCP-Protocol-Version(MCP_PROTOCOL_VERSION_UNSUPPORTED)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증되지 않은 요청. 액세스 토큰이 만료됐으면 CLI 등록의 Authorization 헤더를 새 토큰으로 바꾼다",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Origin 헤더가 있는(브라우저) 요청(MCP_ORIGIN_NOT_ALLOWED)",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    ResponseEntity<Object> post(
            @Parameter(hidden = true) String origin,
            @Parameter(
                    name = "MCP-Protocol-Version",
                    description = "클라이언트가 협상한 MCP 개정판. 없으면 2025-03-26으로 간주한다",
                    in = ParameterIn.HEADER,
                    example = "2025-06-18"
            ) String protocolVersion,
            String body,
            @Parameter(hidden = true) AuthenticatedMember authenticatedMember
    );
    // @formatter:on
}
