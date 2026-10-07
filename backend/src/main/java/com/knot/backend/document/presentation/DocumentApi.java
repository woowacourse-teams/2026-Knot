package com.knot.backend.document.presentation;

import static com.knot.backend.global.config.OpenApiConfig.ACCESS_TOKEN_COOKIE;

import com.knot.backend.auth.domain.AuthenticatedMember;
import com.knot.backend.document.presentation.dto.response.DocumentDetailResponse;
import com.knot.backend.global.response.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;

@Tag(name = "문서", description = "생성된 읽기 전용 문서와 확인 현황 조회")
@SecurityRequirement(name = ACCESS_TOKEN_COOKIE)
public interface DocumentApi {
    // @formatter:off
    @Operation(summary = "문서 상세 정보와 본문 조회",
            description = "현재 Workspace 멤버는 녹음 참여 여부와 관계없이 DRAFT·ARCHIVED 문서를 조회합니다. "
                    + "제목·요약·본문은 읽기 전용이며 조회는 확인·보관 상태를 변경하지 않습니다. "
                    + "확인 대상은 생성 시점에 고정하며 이후 가입자는 NOT_REQUIRED입니다. "
                    + "확인한 대상은 confirmed, 활성 미확인 대상은 pending, 탈퇴한 미확인 대상은 excluded로 집계합니다. "
                    + "다른 주제의 실패·진행 중 작업은 이미 생성된 문서의 조회에 영향을 주지 않습니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "문서 상세 조회 성공",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = DocumentDetailResponse.class))),
            @ApiResponse(responseCode = "400", description = "INVALID_PARAMETER: 경로 ID 형식 오류",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "UNAUTHENTICATED: 로그인하지 않음",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "WORKSPACE_ACCESS_DENIED: 현재 Workspace 멤버가 아님",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "DOCUMENT_NOT_FOUND: 문서가 없거나 Workspace 범위가 다름",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    DocumentDetailResponse findDocument(
            @Parameter(description = "문서가 속한 Workspace ID") Long workspaceId,
            @Parameter(description = "조회할 Document ID") Long documentId,
            @Parameter(hidden = true) AuthenticatedMember authenticatedMember
    );
    // @formatter:on
}
