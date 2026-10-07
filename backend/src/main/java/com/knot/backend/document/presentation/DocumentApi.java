package com.knot.backend.document.presentation;

import static com.knot.backend.global.config.OpenApiConfig.ACCESS_TOKEN_COOKIE;
import static com.knot.backend.global.config.OpenApiConfig.CSRF_TOKEN_HEADER_NAME;

import com.knot.backend.auth.domain.AuthenticatedMember;
import com.knot.backend.document.presentation.dto.response.DocumentConfirmationsResponse;
import com.knot.backend.document.presentation.dto.response.DocumentConfirmationResponse;
import com.knot.backend.document.presentation.dto.response.DocumentDetailResponse;
import com.knot.backend.document.presentation.dto.response.DocumentListResponse;
import com.knot.backend.document.domain.MyConfirmationState;
import com.knot.backend.global.response.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
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
    @Operation(summary = "주제별 문서 폴더와 카드 목록 조회",
            description = "현재 Workspace의 DRAFT·ARCHIVED 문서를 생성 시각 내림차순, 동률이면 ID 내림차순으로 조회합니다. "
                    + "녹음과 내 확인 상태 필터는 AND로 적용합니다. 주제 목록과 문서 수는 필터를 적용한 전체 결과이며 "
                    + "cursor·size는 카드에만 적용합니다. 이후 가입자는 NOT_REQUIRED입니다. "
                    + "커서는 같은 Workspace·멤버·필터에서 이어 사용하고 size는 변경할 수 있습니다. "
                    + "없는 녹음 또는 다른 Workspace의 녹음 조건은 빈 목록을 반환합니다. 조회는 상태를 변경하지 않습니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "문서 목록 조회 성공",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = DocumentListResponse.class))),
            @ApiResponse(responseCode = "400", description = "INVALID_PARAMETER: 쿼리 형식·범위 또는 커서 오류",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "UNAUTHENTICATED: 로그인하지 않음",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "WORKSPACE_ACCESS_DENIED: 현재 Workspace 멤버가 아님",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    DocumentListResponse findDocuments(
            @Parameter(description = "현재 Workspace ID") Long workspaceId,
            @Parameter(description = "이전 응답의 nextCursor. 같은 멤버·조회 조건에서 사용") String cursor,
            @Parameter(description = "한 페이지 카드 수. 기본 50, 최대 100",
                    schema = @Schema(defaultValue = "50", minimum = "1", maximum = "100")) Integer size,
            @Parameter(description = "내 확인 상태 필터. 생략하면 제한 없음") MyConfirmationState myConfirmation,
            @Parameter(description = "원본 녹음 ID 필터. 생략하면 제한 없음", schema = @Schema(minimum = "1")) Long recordingSessionId,
            @Parameter(hidden = true) AuthenticatedMember authenticatedMember
    );

    @Operation(summary = "내 문서 확인 완료 처리",
            description = "현재 Workspace 멤버이면서 생성 당시 고정된 확인 대상만 처리합니다. "
                    + "최초 확인 시각을 유지하며 반복 요청은 현재 문서 상태와 집계를 반환합니다. "
                    + "마지막 필수 대상이 확인 또는 제외되면 같은 트랜잭션에서 ARCHIVED로 전환합니다. "
                    + "확인 취소·본문 수정·수동 보관은 제공하지 않습니다. 요청 본문은 없습니다.",
            parameters = @Parameter(name = CSRF_TOKEN_HEADER_NAME, in = ParameterIn.HEADER, required = true,
                    schema = @Schema(type = "string"), description = "XSRF-TOKEN 쿠키와 일치하는 CSRF 토큰"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "최초 또는 반복 확인 처리 성공",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = DocumentConfirmationResponse.class))),
            @ApiResponse(responseCode = "400", description = "INVALID_PARAMETER: 경로 ID 형식 오류",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "UNAUTHENTICATED: 로그인하지 않음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "WORKSPACE_ACCESS_DENIED: 현재 멤버가 아님, FORBIDDEN: CSRF 오류",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "DOCUMENT_NOT_FOUND: 문서가 없거나 Workspace 범위가 다름",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "CONFIRMATION_NOT_REQUIRED: 고정 확인 대상이 아님",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    DocumentConfirmationResponse confirmDocument(
            @Parameter(description = "문서가 속한 Workspace ID") Long workspaceId,
            @Parameter(description = "확인할 Document ID") Long documentId,
            @Parameter(hidden = true) AuthenticatedMember authenticatedMember
    );

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

    @Operation(summary = "문서 확인 대상과 진행 현황 조회",
            description = "현재 Workspace 멤버는 생성 당시 고정된 확인 대상을 조회합니다. 이후 가입자는 대상에 추가하지 않습니다. "
                    + "CONFIRMED → PENDING → EXCLUDED 순서이며 같은 상태에서는 memberId 오름차순입니다. "
                    + "확인한 탈퇴자는 CONFIRMED를 유지합니다. 전체 집계와 confirmedByMe에는 페이지 범위를 적용하지 않습니다. "
                    + "조회는 확인·보관 상태를 변경하지 않으며 다른 주제의 실패·진행 중 작업에 영향을 받지 않습니다. "
                    + "한 응답의 집계와 목록은 같은 DB 스냅샷으로 읽지만 페이지 요청 사이의 상태 변경에 따른 이동은 가능합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "문서 확인 현황 조회 성공",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = DocumentConfirmationsResponse.class))),
            @ApiResponse(responseCode = "400", description = "INVALID_PARAMETER: 경로·size·cursor 오류 또는 cursor 요청 범위 불일치",
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
    DocumentConfirmationsResponse findDocumentConfirmations(
            @Parameter(description = "문서가 속한 Workspace ID") Long workspaceId,
            @Parameter(description = "조회할 Document ID") Long documentId,
            @Parameter(description = "이전 응답의 nextCursor. 같은 Workspace·문서·Member 요청에 사용") String cursor,
            @Parameter(description = "페이지 크기. 생략하면 50, 허용 범위 1~100",
                    schema = @Schema(type = "integer", minimum = "1", maximum = "100", defaultValue = "50")) Integer size,
            @Parameter(hidden = true) AuthenticatedMember authenticatedMember
    );
    // @formatter:on
}
