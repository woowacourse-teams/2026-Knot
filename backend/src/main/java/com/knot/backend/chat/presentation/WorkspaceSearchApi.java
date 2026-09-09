package com.knot.backend.chat.presentation;

import static com.knot.backend.global.config.OpenApiConfig.BEARER_AUTH;

import com.knot.backend.auth.domain.AuthenticatedMember;
import com.knot.backend.chat.presentation.dto.request.WorkspaceSearchRequest;
import com.knot.backend.chat.presentation.dto.response.WorkspaceSearchResponse;
import com.knot.backend.global.response.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

@Tag(name = "Workspace Search", description = "CLI 코딩 에이전트 경유 탐색의 서버 검색 단계. 저장·LLM 호출 없이 근거 청크와 규칙 문장을 돌려준다")
@SecurityRequirement(name = BEARER_AUTH)
public interface WorkspaceSearchApi {

    // @formatter:off
    @Operation(
            summary = "Workspace 검색",
            description = "Workspace의 공개된 문서에서 질문과 관련도 높은 청크(최대 top-k)를 고른다. "
                    + "세션·메시지를 만들지 않고 아무것도 저장하지 않으므로 같은 질문을 여러 번 보내도 된다. "
                    + "근거가 없거나 질문 범위가 넓으면 안내 문구만 돌려준다"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "검색 완료. status가 READY면 chunks, 아니면 fallbackAnswer를 쓴다",
                    headers = @Header(
                            name = HttpHeaders.CACHE_CONTROL,
                            description = "사용자별 검색 응답 캐시 방지",
                            schema = @Schema(type = "string", example = "no-store")
                    ),
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = WorkspaceSearchResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "질문이 비어 있거나 10,000자를 넘음",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증되지 않은 요청",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "워크스페이스 멤버가 아님(WORKSPACE_ACCESS_DENIED)",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "워크스페이스를 찾을 수 없음(WORKSPACE_NOT_FOUND)",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "공개된 문서가 없음(CHAT_DOCUMENTS_NOT_READY)",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "임베딩 provider 호출 실패(SEARCH_PROVIDER_FAILED) 또는 검색 설정 오류(SEARCH_CONFIGURATION_INVALID)",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    ResponseEntity<WorkspaceSearchResponse> search(
            @Parameter(
                    description = "워크스페이스 ID",
                    in = ParameterIn.PATH,
                    example = "1",
                    required = true
            ) long workspaceId,
            WorkspaceSearchRequest request,
            @Parameter(hidden = true) AuthenticatedMember authenticatedMember
    );
    // @formatter:on
}
