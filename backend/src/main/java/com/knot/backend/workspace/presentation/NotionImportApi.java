package com.knot.backend.workspace.presentation;

import com.knot.backend.auth.domain.AuthenticatedMember;
import com.knot.backend.global.config.OpenApiConfig;
import com.knot.backend.global.response.ErrorResponse;
import com.knot.backend.workspace.presentation.dto.response.NotionImportStatusResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

@Tag(name = "Notion Import", description = "Notion 문서 가져오기 실행 상태 조회")
public interface NotionImportApi {

    // @formatter:off
    @Operation(
            summary = "Notion Import 상태 조회",
            security = @SecurityRequirement(name = OpenApiConfig.ACCESS_TOKEN_COOKIE)
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Notion Import 상태 조회 성공",
                    headers = @Header(
                            name = HttpHeaders.CACHE_CONTROL,
                            description = "상태 응답 캐시 방지",
                            schema = @Schema(type = "string", example = "no-store")
                    ),
                    content = @Content(schema = @Schema(implementation = NotionImportStatusResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "잘못된 Import Run ID",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 필요",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Import Run이 없거나 조회할 수 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    // @formatter:on
    ResponseEntity<NotionImportStatusResponse> status(
            @Parameter(description = "Notion Import 실행 ID", example = "1") Long importRunId,
            @Parameter(hidden = true) AuthenticatedMember authenticatedMember
    );
}
