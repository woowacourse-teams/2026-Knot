package com.knot.backend.recording.presentation;

import static com.knot.backend.global.config.OpenApiConfig.ACCESS_TOKEN_COOKIE;

import com.knot.backend.auth.domain.AuthenticatedMember;
import com.knot.backend.global.response.ErrorResponse;
import com.knot.backend.recording.presentation.dto.response.RecordingCurrentResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

@Tag(name = "녹음", description = "본인 녹음 세션")
@SecurityRequirement(name = ACCESS_TOKEN_COOKIE)
public interface RecordingCurrentApi {

    // @formatter:off
    @Operation(
            summary = "내 현재 녹음 조회",
            description = "요청한 Workspace에서 본인이 진행 중이거나 일시정지한 녹음 한 건을 반환한다. "
                    + "다른 탭·기기에서도 조회할 수 있지만 생존 신호를 갱신하거나 제어 권한을 넘기지 않고 제어 비밀값을 반환하지 않는다. "
                    + "다른 Workspace의 활성 녹음은 반환하지 않는다. 종료 후 업로드·문서 처리 상태는 아직 반환하지 않는다"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "진행 중이거나 일시정지한 본인 녹음",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = RecordingCurrentResponse.class))),
            @ApiResponse(responseCode = "204", description = "요청한 Workspace에 본인의 활성 녹음 없음", content = @Content),
            @ApiResponse(responseCode = "400", description = "워크스페이스 ID 형식 오류",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "인증되지 않은 요청",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "워크스페이스 소속 없음",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "워크스페이스 없음",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<RecordingCurrentResponse> findCurrent(
            long workspaceId,
            @Parameter(hidden = true) AuthenticatedMember authenticatedMember
    );
    // @formatter:on
}
