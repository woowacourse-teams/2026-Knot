package com.knot.backend.workspace.presentation;

import com.knot.backend.auth.domain.AuthenticatedMember;
import com.knot.backend.global.config.OpenApiConfig;
import com.knot.backend.global.response.ErrorResponse;
import com.knot.backend.workspace.application.WorkspaceInvitationService;
import com.knot.backend.workspace.application.dto.result.WorkspaceInvitationResult;
import com.knot.backend.workspace.presentation.dto.response.WorkspaceInvitationResponse;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "워크스페이스 초대", description = "워크스페이스 초대 코드와 링크 발급, 조회, 재발급")
@SecurityRequirement(name = OpenApiConfig.ACCESS_TOKEN_COOKIE)
@RestController
@RequestMapping("/workspaces/{workspaceId}")
public class WorkspaceInvitationController {
    private final WorkspaceInvitationService workspaceInvitationService;

    public WorkspaceInvitationController(WorkspaceInvitationService workspaceInvitationService) {
        this.workspaceInvitationService = workspaceInvitationService;
    }

    // @formatter:off
    @Operation(
            summary = "워크스페이스 초대 발급",
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
                    description = "기존 활성 초대 반환",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = WorkspaceInvitationResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "201",
                    description = "새 초대 생성",
                    headers = @Header(
                            name = HttpHeaders.LOCATION,
                            description = "생성된 초대 조회 URI",
                            schema = @Schema(type = "string", format = "uri")
                    ),
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = WorkspaceInvitationResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "잘못된 Workspace ID",
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
                    description = "CSRF 검증 실패 또는 워크스페이스 접근 거부",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "워크스페이스 없음",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "활성 초대 복구 실패",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )})
    // @formatter:on
    @PostMapping("/invitations")
    public ResponseEntity<WorkspaceInvitationResponse> issue(
            @PathVariable Long workspaceId,
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember
    ) {
        WorkspaceInvitationResult result = workspaceInvitationService.issue(
                workspaceId,
                authenticatedMember.getMemberId()
        );
        ResponseEntity.BodyBuilder responseBuilder = result.created()
                ? ResponseEntity.created(invitationUri(workspaceId))
                : ResponseEntity.ok();
        return responseBuilder.body(WorkspaceInvitationResponse.from(result));
    }

    // @formatter:off
    @Operation(summary = "워크스페이스 초대 조회")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "활성 초대 조회",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = WorkspaceInvitationResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "잘못된 Workspace ID",
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
                    description = "워크스페이스 접근 거부",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "워크스페이스 또는 활성 초대 없음",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "활성 초대 복구 실패",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )})
    // @formatter:on
    @GetMapping("/invitation")
    public WorkspaceInvitationResponse get(
            @PathVariable Long workspaceId,
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember
    ) {
        return WorkspaceInvitationResponse.from(
                workspaceInvitationService.get(
                        workspaceId,
                        authenticatedMember.getMemberId()
                )
        );
    }

    // @formatter:off
    @Operation(
            summary = "워크스페이스 초대 재발급",
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
                    responseCode = "201",
                    description = "새 초대 생성",
                    headers = @Header(
                            name = HttpHeaders.LOCATION,
                            description = "생성된 초대 조회 URI",
                            schema = @Schema(type = "string", format = "uri")
                    ),
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = WorkspaceInvitationResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "잘못된 Workspace ID",
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
                    description = "CSRF 검증 실패 또는 워크스페이스 접근 거부",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "워크스페이스 없음",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "초대 생성 실패",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )})
    // @formatter:on
    @PostMapping("/invitations/reissue")
    public ResponseEntity<WorkspaceInvitationResponse> reissue(
            @PathVariable Long workspaceId,
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember
    ) {
        WorkspaceInvitationResult result = workspaceInvitationService.reissue(
                workspaceId,
                authenticatedMember.getMemberId()
        );
        return ResponseEntity.created(invitationUri(workspaceId))
                .body(WorkspaceInvitationResponse.from(result));
    }

    private URI invitationUri(Long workspaceId) {
        return URI.create("/workspaces/" + workspaceId + "/invitation");
    }
}
