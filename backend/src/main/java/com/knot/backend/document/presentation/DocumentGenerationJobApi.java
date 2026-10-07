package com.knot.backend.document.presentation;

import static com.knot.backend.global.config.OpenApiConfig.ACCESS_TOKEN_COOKIE;

import com.knot.backend.auth.domain.AuthenticatedMember;
import com.knot.backend.document.presentation.dto.response.DocumentGenerationJobListResponse;
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

@Tag(name = "문서 생성 작업", description = "문서 생성 진행·실패 상태 조회")
@SecurityRequirement(name = ACCESS_TOKEN_COOKIE)
public interface DocumentGenerationJobApi {

    // @formatter:off
    @Operation(summary = "내 녹음의 문서 생성 작업 목록 조회",
            description = "현재 Workspace에서 로그인한 멤버가 시작한 녹음의 QUEUED·RUNNING·마지막 실패 후 7일 미만인 FAILED를 반환합니다. "
                    + "다른 멤버의 녹음 작업은 반환하지 않습니다. 녹음 제목 필드는 제공하지 않습니다. "
                    + "성공·만료된 실패 작업은 제외하며 생성 시각 내림차순, 동률이면 Job ID 내림차순입니다. "
                    + "한 녹음의 여러 작업을 개별 반환합니다. 홈의 한 카드 선택과 종합 상태 판단은 녹음 조회 계약에서 연결합니다. "
                    + "조회는 상태를 변경하거나 자료를 삭제하지 않습니다. "
                    + "목록에서 사라진 사실만으로 녹음 전체의 완료를 판단하지 않으며 녹음 상세의 종합 결과를 확인합니다. "
                    + "페이지 요청 사이에 실행 상태와 만료 여부가 바뀔 수 있습니다. 요청 본문은 없습니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "생성 작업 목록 조회 성공",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = DocumentGenerationJobListResponse.class))),
            @ApiResponse(responseCode = "400", description = "INVALID_PARAMETER: 경로·size·cursor 오류 또는 커서 조회 범위 불일치",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "UNAUTHENTICATED: 로그인하지 않음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "WORKSPACE_ACCESS_DENIED: 현재 Workspace 멤버가 아님",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    DocumentGenerationJobListResponse findDocumentGenerationJobs(
            @Parameter(description = "현재 Workspace ID") Long workspaceId,
            @Parameter(description = "같은 Workspace·Member 조회의 이전 nextCursor") String cursor,
            @Parameter(description = "생략하면 20, 허용 범위 1~100",
                    schema = @Schema(type = "integer", minimum = "1", maximum = "100", defaultValue = "20")) Integer size,
            @Parameter(hidden = true) AuthenticatedMember authenticatedMember
    );
    // @formatter:on
}
