package com.knot.backend.workspace.presentation;

import static com.knot.backend.global.config.OpenApiConfig.ACCESS_TOKEN_COOKIE;

import com.knot.backend.auth.domain.AuthenticatedMember;
import com.knot.backend.global.response.ErrorResponse;
import com.knot.backend.workspace.presentation.dto.response.WorkspaceOwnershipTransferCandidateResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.MediaType;

@Tag(name = "워크스페이스", description = "워크스페이스 생성 및 조회")
@SecurityRequirement(name = ACCESS_TOKEN_COOKIE)
public interface WorkspaceOwnershipTransferCandidateApi {

    // @formatter:off
    @Operation(
            summary = "워크스페이스 OWNER 승계 후보 조회",
            description = "현재 OWNER만 본인과 탈퇴 이력을 제외한 활성 MEMBER를 memberId 오름차순으로 조회합니다. "
                    + "후보가 없으면 빈 배열을 반환합니다. 응답은 조회 시점의 스냅샷이며, "
                    + "승계 API는 잠금 후 후보의 활성 참여와 권한을 재검증해야 합니다. "
                    + "대상 탈퇴나 권한 변경으로 승계가 거절되면 후보를 다시 조회합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "승계 후보 조회 성공",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            array = @ArraySchema(schema = @Schema(
                                    implementation = WorkspaceOwnershipTransferCandidateResponse.class
                            ))
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "워크스페이스 ID 형식 오류",
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
                    description = "활성 OWNER 권한 없음",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "존재하지 않거나 삭제된 워크스페이스",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    List<WorkspaceOwnershipTransferCandidateResponse> findCandidates(
            @Parameter(description = "워크스페이스 ID", schema = @Schema(minimum = "1")) Long workspaceId,
            @Parameter(hidden = true) AuthenticatedMember authenticatedMember
    );
    // @formatter:on
}
