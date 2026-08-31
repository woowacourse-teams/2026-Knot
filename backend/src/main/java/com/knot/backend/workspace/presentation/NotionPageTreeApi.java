package com.knot.backend.workspace.presentation;

import com.knot.backend.auth.domain.AuthenticatedMember;
import com.knot.backend.global.config.OpenApiConfig;
import com.knot.backend.global.response.ErrorResponse;
import com.knot.backend.workspace.presentation.dto.response.NotionPageTreeItemResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

@Tag(name = "Notion Page", description = "마지막 성공 Import로 발행된 Notion Page 조회")
public interface NotionPageTreeApi {

    // @formatter:off
    @Operation(
            summary = "Notion Page Tree 조회",
            security = @SecurityRequirement(name = OpenApiConfig.ACCESS_TOKEN_COOKIE)
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Page Tree 조회 성공, 발행된 Page가 없으면 빈 배열",
                    headers = @Header(
                            name = HttpHeaders.CACHE_CONTROL,
                            description = "Page Tree 응답 캐시 방지",
                            schema = @Schema(type = "string", example = "no-store")
                    ),
                    content = @Content(
                            array = @ArraySchema(
                                    schema = @Schema(implementation = NotionPageTreeItemResponse.class)
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "잘못된 Workspace ID 값 또는 형식",
                    headers = @Header(
                            name = HttpHeaders.CACHE_CONTROL,
                            description = "오류 응답 캐시 방지",
                            schema = @Schema(type = "string", example = "no-store")
                    ),
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "invalidWorkspaceId",
                                            summary = "Workspace ID 값 오류",
                                            value = """
                                                    {
                                                      "code": "INVALID_WORKSPACE_ID",
                                                      "message": "워크스페이스 ID가 올바르지 않습니다"
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "invalidParameter",
                                            summary = "Workspace ID 형식 오류",
                                            value = """
                                                    {
                                                      "code": "INVALID_PARAMETER",
                                                      "message": "요청 파라미터 형식이 올바르지 않습니다"
                                                    }
                                                    """
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 필요",
                    headers = @Header(
                            name = HttpHeaders.CACHE_CONTROL,
                            description = "오류 응답 캐시 방지",
                            schema = @Schema(type = "string", example = "no-store")
                    ),
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "unauthenticated",
                                    summary = "인증 필요",
                                    value = """
                                            {
                                              "code": "UNAUTHENTICATED",
                                              "message": "인증이 필요합니다"
                                            }
                                            """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Workspace 멤버가 아님",
                    headers = @Header(
                            name = HttpHeaders.CACHE_CONTROL,
                            description = "오류 응답 캐시 방지",
                            schema = @Schema(type = "string", example = "no-store")
                    ),
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "workspaceAccessDenied",
                                    summary = "Workspace 접근 권한 없음",
                                    value = """
                                            {
                                              "code": "WORKSPACE_ACCESS_DENIED",
                                              "message": "워크스페이스에 접근할 수 없습니다"
                                            }
                                            """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Workspace가 없음",
                    headers = @Header(
                            name = HttpHeaders.CACHE_CONTROL,
                            description = "오류 응답 캐시 방지",
                            schema = @Schema(type = "string", example = "no-store")
                    ),
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "workspaceNotFound",
                                    summary = "Workspace 없음",
                                    value = """
                                            {
                                              "code": "WORKSPACE_NOT_FOUND",
                                              "message": "워크스페이스를 찾을 수 없습니다"
                                            }
                                            """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Page 계층 데이터가 올바르지 않음",
                    headers = @Header(
                            name = HttpHeaders.CACHE_CONTROL,
                            description = "오류 응답 캐시 방지",
                            schema = @Schema(type = "string", example = "no-store")
                    ),
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "notionPageTreeInvalid",
                                    summary = "Page 계층 데이터 오류",
                                    value = """
                                            {
                                              "code": "NOTION_PAGE_TREE_INVALID",
                                              "message": "Notion Page Tree를 조회할 수 없습니다"
                                            }
                                            """
                            )
                    )
            )
    })
    // @formatter:on
    ResponseEntity<List<NotionPageTreeItemResponse>> tree(
            @Parameter(description = "Workspace ID", example = "1") Long workspaceId,
            @Parameter(hidden = true) AuthenticatedMember authenticatedMember
    );
}
