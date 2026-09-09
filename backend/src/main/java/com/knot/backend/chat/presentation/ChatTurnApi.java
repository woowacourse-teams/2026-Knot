package com.knot.backend.chat.presentation;

import static com.knot.backend.global.config.OpenApiConfig.BEARER_AUTH;

import com.knot.backend.auth.domain.AuthenticatedMember;
import com.knot.backend.chat.presentation.dto.request.SaveChatTurnRequest;
import com.knot.backend.chat.presentation.dto.response.ChatTurnResponse;
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
import org.springframework.http.ResponseEntity;

@Tag(name = "Chat Turn", description = "CLI 에이전트가 만든 질문·답변·근거를 세션에 저장한다. LLM·검색을 부르지 않는다")
@SecurityRequirement(name = BEARER_AUTH)
public interface ChatTurnApi {

    // @formatter:off
    @Operation(
            summary = "턴 저장",
            description = "질문을 USER 메시지로, 답변을 ASSISTANT 메시지(generated_by=CLIENT)로, 근거(최대 8개, 배열 순서가 rank)를 "
                    + "search_references로 한 트랜잭션에 저장한다. 근거는 세션이 속한 Workspace의 페이지여야 하며 "
                    + "서버가 돌려준 검색 결과의 부분집합인지는 검증하지 않는다"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "저장 완료",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ChatTurnResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "질문·답변이 비어 있거나 질문이 10,000자를 넘거나 근거가 8개를 넘음(VALIDATION_ERROR), "
                            + "근거가 이 Workspace의 문서가 아니거나 같은 청크가 중복됨(CHAT_TURN_REFERENCE_INVALID)",
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
                    description = "이전 질문의 답변이 진행 중(CHAT_TURN_IN_PROGRESS)",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    ResponseEntity<ChatTurnResponse> save(
            @Parameter(
                    description = "채팅 세션 ID",
                    in = ParameterIn.PATH,
                    example = "10",
                    required = true
            ) long sessionId,
            SaveChatTurnRequest request,
            @Parameter(hidden = true) AuthenticatedMember authenticatedMember
    );
    // @formatter:on
}
