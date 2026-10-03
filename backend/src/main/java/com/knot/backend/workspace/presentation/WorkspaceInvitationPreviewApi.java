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
    @Operation(
            summary = "초대 토큰 또는 코드로 참여 대상 워크스페이스 조회",
            description = "인증 없이 참여 대상을 확인하며 가입하거나 초대를 소비하지 않습니다. "
                    + "코드와 링크는 같은 초대의 생성 24시간 뒤 함께 만료하고 만료 시각부터 조회할 수 없습니다. "
                    + "FE는 6자리 A-Z 코드 완성 또는 링크 진입 시 조회하고, 참여는 사용자 확인 뒤 별도로 요청합니다. "
                    + "입력 변경 시 오래된 응답을 무시하고 같은 완성값의 중복 요청을 억제합니다. "
                    + "잘못되거나 만료된 링크는 오류 화면 후 코드 입력으로 안내합니다. 모든 응답은 no-store입니다."
    )
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
                    description = "형식 오류, 미존재, 공통 만료, 과거 무효화 또는 삭제된 워크스페이스",
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
                            schema = @Schema(
                                type = "integer",
                                minimum = "1"
                            )
                    ),
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    WorkspaceInvitationPreviewResponse preview(
            @Parameter(description = "원문이 정확히 일치하는 링크 토큰 또는 6자리 A-Z 코드. "
                    + "기존 숫자 포함 코드와 소문자 정규화는 만료 전까지 호환합니다.") String tokenOrCode,
            @Parameter(hidden = true) HttpServletRequest request
    );
    // @formatter:on
}
