package com.knot.backend.chat.presentation;

import static com.knot.backend.global.config.OpenApiConfig.BEARER_AUTH;

import com.knot.backend.auth.domain.AuthenticatedMember;
import com.knot.backend.chat.presentation.dto.request.SearchChatMessageRequest;
import com.knot.backend.chat.presentation.dto.response.ChatSearchResponse;
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

@Tag(name = "Chat Search", description = "데스크톱 경유 탐색의 서버 검색 단계. LLM을 부르지 않고 근거 청크와 규칙 문장을 돌려준다")
@SecurityRequirement(name = BEARER_AUTH)
public interface ChatSearchApi {

    // @formatter:off
    @Operation(
            summary = "탐색 검색",
            description = "질문을 USER 메시지로 저장하고 공개된 문서에서 관련도 상위 청크(최대 top-k)를 고른다. "
                    + "근거가 없거나 질문 범위가 넓으면 안내 답변을 ASSISTANT로 저장한 뒤 그 내용을 돌려준다"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "검색 완료. status가 READY면 chunks, 아니면 fallbackAnswer를 쓴다",
                    headers = @Header(
                            name = HttpHeaders.CACHE_CONTROL,
                            description = "사용자별 검색 응답 캐시 방지",
                            schema = @Schema(type = "string", example = "no-store")
                    ),
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ChatSearchResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "질문이 비어 있거나 10,000자를 넘음",
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
                    description = "채팅 세션의 소유자가 아님(CHAT_ACCESS_DENIED)",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "채팅 세션을 찾을 수 없음(CHAT_SESSION_NOT_FOUND)",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "공개된 문서가 없음(CHAT_DOCUMENTS_NOT_READY) 또는 이전 질문의 답변이 진행 중(CHAT_TURN_IN_PROGRESS)",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    ResponseEntity<ChatSearchResponse> search(
            @Parameter(
                    description = "채팅 세션 ID",
                    in = ParameterIn.PATH,
                    example = "10",
                    required = true
            ) long sessionId,
            SearchChatMessageRequest request,
            @Parameter(hidden = true) AuthenticatedMember authenticatedMember
    );
    // @formatter:on
}
