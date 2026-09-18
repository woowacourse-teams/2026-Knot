package com.knot.backend.workspace.presentation;

import com.knot.backend.global.response.ErrorResponse;
import com.knot.backend.workspace.presentation.dto.response.WorkspaceInvitationPreviewResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

@Tag(name = "워크스페이스 초대", description = "워크스페이스 초대 코드와 링크 발급, 조회, 재발급")
public interface WorkspaceInvitationPreviewApi {

    // @formatter:off
    @Operation(summary = "초대 토큰 또는 코드로 참여 대상 워크스페이스 조회")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "참여 대상 워크스페이스 조회 성공",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = WorkspaceInvitationPreviewResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "초대 없음",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "429",
                    description = "초대 코드 조회 요청 제한 초과",
                    headers = @Header(
                            name = HttpHeaders.RETRY_AFTER,
                            description = "다음 코드 조회 요청까지 대기할 초",
                            schema = @Schema(type = "integer", minimum = "1")
                    ),
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    WorkspaceInvitationPreviewResponse preview(
            @Parameter(description = "초대 링크 토큰 또는 6자리 코드") String tokenOrCode,
            @Parameter(hidden = true) HttpServletRequest request
    );
    // @formatter:on
}
