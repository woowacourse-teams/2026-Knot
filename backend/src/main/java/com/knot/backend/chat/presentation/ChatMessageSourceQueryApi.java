package com.knot.backend.chat.presentation;

import static com.knot.backend.global.config.OpenApiConfig.ACCESS_TOKEN_COOKIE;

import com.knot.backend.auth.domain.AuthenticatedMember;
import com.knot.backend.chat.presentation.dto.response.SearchReferencesResponse;
import com.knot.backend.global.response.ErrorResponse;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

@Tag(name = "Chat Message", description = "AI 답변 메시지의 검색 출처 조회")
@SecurityRequirement(name = ACCESS_TOKEN_COOKIE)
public interface ChatMessageSourceQueryApi {

    // @formatter:off
    @Operation(summary = "AI 답변 출처 조회")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "저장된 검색 출처 조회 성공. 출처가 없으면 빈 배열",
                    headers = @Header(
                            name = HttpHeaders.CACHE_CONTROL,
                            description = "사용자별 출처 응답 캐시 방지",
                            schema = @Schema(type = "string", example = "no-store")
                    ),
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = SearchReferencesResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "메시지 ID가 올바르지 않거나 형식이 잘못됨",
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
                    description = "메시지가 속한 채팅 세션의 소유자가 아님",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "메시지를 찾을 수 없거나 assistant 메시지가 아님",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    ResponseEntity<SearchReferencesResponse> findSources(
            @Parameter(
                    description = "AI 답변 메시지 ID",
                    in = ParameterIn.PATH,
                    example = "102",
                    required = true
            ) Long messageId,
            @Parameter(hidden = true) AuthenticatedMember authenticatedMember
    );
    // @formatter:on
}
