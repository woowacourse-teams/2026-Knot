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
                    description = "잘못된 Workspace ID",
                    headers = @Header(
                            name = HttpHeaders.CACHE_CONTROL,
                            description = "오류 응답 캐시 방지",
                            schema = @Schema(type = "string", example = "no-store")
                    ),
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 필요",
                    headers = @Header(
                            name = HttpHeaders.CACHE_CONTROL,
                            description = "오류 응답 캐시 방지",
                            schema = @Schema(type = "string", example = "no-store")
                    ),
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Workspace 멤버가 아님",
                    headers = @Header(
                            name = HttpHeaders.CACHE_CONTROL,
                            description = "오류 응답 캐시 방지",
                            schema = @Schema(type = "string", example = "no-store")
                    ),
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Workspace가 없음",
                    headers = @Header(
                            name = HttpHeaders.CACHE_CONTROL,
                            description = "오류 응답 캐시 방지",
                            schema = @Schema(type = "string", example = "no-store")
                    ),
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Page 계층 데이터가 올바르지 않음",
                    headers = @Header(
                            name = HttpHeaders.CACHE_CONTROL,
                            description = "오류 응답 캐시 방지",
                            schema = @Schema(type = "string", example = "no-store")
                    ),
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    // @formatter:on
    ResponseEntity<List<NotionPageTreeItemResponse>> tree(
            @Parameter(description = "Workspace ID", example = "1") Long workspaceId,
            @Parameter(hidden = true) AuthenticatedMember authenticatedMember
    );
}
