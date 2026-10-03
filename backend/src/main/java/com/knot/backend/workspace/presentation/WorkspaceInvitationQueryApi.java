package com.knot.backend.workspace.presentation;

import static com.knot.backend.global.config.OpenApiConfig.ACCESS_TOKEN_COOKIE;

import com.knot.backend.auth.domain.AuthenticatedMember;
import com.knot.backend.global.response.ErrorResponse;
import com.knot.backend.workspace.presentation.dto.response.WorkspaceInvitationSummaryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.MediaType;

@Tag(name = "워크스페이스", description = "워크스페이스 생성 및 조회")
@SecurityRequirement(name = ACCESS_TOKEN_COOKIE)
public interface WorkspaceInvitationQueryApi {

    // @formatter:off
    @Operation(
            summary = "OWNER의 유효 초대 목록 조회",
            description = "현재 활성 OWNER만 공통 만료 전 초대를 생성 시각·ID 내림차순으로 조회합니다. "
                    + "없으면 빈 배열이며 코드·링크 원문과 암호문을 제공하지 않습니다. "
                    + "만료 정각·과거 무효 초대는 제외하고 Cache-Control: no-store를 반환합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "유효 초대 목록 조회 성공",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            array = @ArraySchema(schema = @Schema(
                                    implementation = WorkspaceInvitationSummaryResponse.class
                            ))
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "워크스페이스 ID 형식 오류",
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
                    description = "활성 OWNER 권한 없음",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "존재하지 않거나 삭제된 워크스페이스",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    List<WorkspaceInvitationSummaryResponse> findAll(
            @Parameter(description = "워크스페이스 ID", schema = @Schema(minimum = "1")) Long workspaceId,
            @Parameter(hidden = true) AuthenticatedMember authenticatedMember
    );
    // @formatter:on
}
