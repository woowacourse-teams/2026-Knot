package com.knot.backend.search.presentation;

import static com.knot.backend.global.config.OpenApiConfig.ACCESS_TOKEN_COOKIE;

import com.knot.backend.auth.domain.AuthenticatedMember;
import com.knot.backend.global.response.ErrorResponse;
import com.knot.backend.search.presentation.dto.response.SearchConversationListResponse;
import com.knot.backend.search.presentation.dto.response.SearchMessageListResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;

@Tag(name = "탐색", description = "Search V2 개인 대화 조회")
@SecurityRequirement(name = ACCESS_TOKEN_COOKIE)
public interface SearchConversationApi {

    @Operation(summary = "내 탐색 대화 목록 조회", description = "현재 Workspace의 활성 멤버가 본인 대화만 읽습니다. "
            + "updatedAt 내림차순, 동률이면 ID 내림차순입니다. 빈 대화와 첫 답변 실패로 숨긴 대화는 제외합니다. "
            + "커서는 같은 Workspace와 멤버 범위에서 사용하며 페이지 크기는 변경할 수 있습니다. "
            + "조회는 저장된 상태를 변경하지 않습니다. 페이지 사이 활동으로 정렬이 바뀔 수 있습니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "목록 조회 성공", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = SearchConversationListResponse.class))),
            @ApiResponse(responseCode = "400", description = "INVALID_PARAMETER: size·cursor·경로 형식 또는 범위 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "UNAUTHENTICATED: 로그인하지 않음", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "WORKSPACE_ACCESS_DENIED: 현재 Workspace 멤버가 아님", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))})
    SearchConversationListResponse findConversations(
            @Parameter(description = "Workspace ID", schema = @Schema(minimum = "1")) Long workspaceId,
            @Parameter(description = "이전 응답의 nextCursor. 같은 Workspace·Member 범위") String cursor,
            @Parameter(description = "페이지 크기", schema = @Schema(defaultValue = "20", minimum = "1", maximum = "100")) Integer size,
            @Parameter(hidden = true) AuthenticatedMember authenticatedMember
    );

    @Operation(summary = "내 탐색 대화 메시지 조회", description = "활성 Workspace 멤버인 대화 소유자만 읽습니다. "
            + "최신 메시지부터 size개를 선택하고 sequence 오름차순으로 반환합니다. " + "previousCursor를 정수로 변환해 다음 요청의 beforeSequence에 전달합니다. "
            + "본문과 부분 답변은 그대로 반환하고 근거는 같은 Workspace의 Document 카드만 rank 순으로 반환합니다. "
            + "목록에서 숨겨진 대화도 소유자는 조회할 수 있으며 조회는 저장 상태를 변경하지 않습니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "메시지 조회 성공", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = SearchMessageListResponse.class))),
            @ApiResponse(responseCode = "400", description = "INVALID_PARAMETER: size·beforeSequence·경로 형식 또는 범위 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "UNAUTHENTICATED: 로그인하지 않음", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "SEARCH_ACCESS_DENIED: 활성 멤버가 아니거나 대화 소유·Workspace 범위 불일치", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "CONVERSATION_NOT_FOUND: 대화가 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))})
    SearchMessageListResponse findMessages(
            @Parameter(description = "Workspace ID", schema = @Schema(minimum = "1")) Long workspaceId,
            @Parameter(description = "본인 대화 ID", schema = @Schema(minimum = "1")) Long conversationId,
            @Parameter(description = "이 sequence보다 앞선 메시지 조회", schema = @Schema(minimum = "1")) Integer beforeSequence,
            @Parameter(description = "페이지 크기", schema = @Schema(defaultValue = "30", minimum = "1", maximum = "100")) Integer size,
            @Parameter(hidden = true) AuthenticatedMember authenticatedMember
    );
}
