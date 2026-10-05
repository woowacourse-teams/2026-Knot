package com.knot.backend.workspace.presentation;

import static com.knot.backend.global.config.OpenApiConfig.ACCESS_TOKEN_COOKIE;

import com.knot.backend.auth.domain.AuthenticatedMember;
import com.knot.backend.global.config.OpenApiConfig;
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

@Tag(name = "워크스페이스", description = "워크스페이스 생성 및 조회")
@SecurityRequirement(name = ACCESS_TOKEN_COOKIE)
public interface WorkspaceDeletionApi {

    // @formatter:off
    @Operation(
            summary = "워크스페이스 삭제",
            description = "현재 OWNER만 삭제할 수 있습니다. 모든 멤버의 참여를 종료하고 "
                    + "진행 중·일시정지 녹음은 저장 없이 DISCARDED로 바꿉니다.",
            parameters = @Parameter(
                    name = OpenApiConfig.CSRF_TOKEN_HEADER_NAME,
                    in = ParameterIn.HEADER,
                    required = true,
                    schema = @Schema(type = "string"),
                    description = "CSRF 토큰"
            )
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "워크스페이스 삭제 성공"),
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
                    description = "활성 멤버가 아님, OWNER가 아님 또는 CSRF 검증 실패",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "존재하지 않거나 이미 삭제된 워크스페이스",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    void delete(
            @Parameter(description = "워크스페이스 ID", schema = @Schema(minimum = "1")) Long workspaceId,
            @Parameter(hidden = true) AuthenticatedMember authenticatedMember
    );
    // @formatter:on
}
