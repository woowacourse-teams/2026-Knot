package com.knot.backend.workspace.presentation;

import com.knot.backend.auth.domain.AuthenticatedMember;
import com.knot.backend.global.config.OpenApiConfig;
import com.knot.backend.global.response.ErrorResponse;
import com.knot.backend.workspace.application.WorkspaceInvitationAcceptanceService;
import com.knot.backend.workspace.application.dto.result.WorkspaceInvitationAcceptanceResult;
import com.knot.backend.workspace.presentation.dto.request.WorkspaceInvitationAcceptanceRequest;
import com.knot.backend.workspace.presentation.dto.response.WorkspaceInvitationAcceptanceResponse;
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
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "워크스페이스 초대", description = "워크스페이스 초대 코드와 링크 발급, 조회, 재발급")
@SecurityRequirement(name = OpenApiConfig.ACCESS_TOKEN_COOKIE)
@RestController
@RequestMapping("/api/v1/invitations")
public class WorkspaceInvitationAcceptanceController {
    private final WorkspaceInvitationAcceptanceService workspaceInvitationAcceptanceService;

    public WorkspaceInvitationAcceptanceController(
            WorkspaceInvitationAcceptanceService workspaceInvitationAcceptanceService
    ) {
        this.workspaceInvitationAcceptanceService = workspaceInvitationAcceptanceService;
    }

    // @formatter:off
    @Operation(
            summary = "초대 코드 또는 링크 토큰으로 워크스페이스 참여",
            parameters = @Parameter(
                    name = "X-XSRF-TOKEN",
                    description = "CSRF 토큰",
                    in = ParameterIn.HEADER,
                    required = true,
                    schema = @Schema(type = "string")
            )
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "기존 멤버십 반환",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = WorkspaceInvitationAcceptanceResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "201",
                    description = "새 멤버십 생성",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = WorkspaceInvitationAcceptanceResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "잘못된 요청 본문",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 필요",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "CSRF 검증 실패",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)
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
                    description = "초대 코드 참여 요청 제한 초과",
                    headers = @Header(
                            name = HttpHeaders.RETRY_AFTER,
                            description = "다음 코드 참여 요청까지 대기할 초",
                            schema = @Schema(type = "integer", minimum = "1")
                    ),
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )})
    // @formatter:on
    @PostMapping("/accept")
    public ResponseEntity<WorkspaceInvitationAcceptanceResponse> accept(
            @Valid @RequestBody WorkspaceInvitationAcceptanceRequest request,
            HttpServletRequest httpServletRequest,
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember
    ) {
        WorkspaceInvitationAcceptanceResult result = workspaceInvitationAcceptanceService.accept(
                request.credential(),
                httpServletRequest.getRemoteAddr(),
                authenticatedMember.getMemberId()
        );
        WorkspaceInvitationAcceptanceResponse response = WorkspaceInvitationAcceptanceResponse.from(result);
        if (result.created()) {
            return ResponseEntity.status(201)
                    .body(response);
        }
        return ResponseEntity.ok(response);
    }
}
