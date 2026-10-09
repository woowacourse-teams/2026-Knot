package com.knot.backend.recording.presentation;

import static com.knot.backend.global.config.OpenApiConfig.ACCESS_TOKEN_COOKIE;

import com.knot.backend.auth.domain.AuthenticatedMember;
import com.knot.backend.global.response.ErrorResponse;
import com.knot.backend.recording.presentation.dto.response.RecordingDetailResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;

@Tag(name = "녹음", description = "본인 녹음 세션")
@SecurityRequirement(name = ACCESS_TOKEN_COOKIE)
public interface RecordingDetailApi {

    // @formatter:off
    @Operation(
            summary = "내 녹음 단건 조회",
            description = "알고 있는 recordingId로 본인 녹음의 세션 상태·시간과 최종 오디오 업로드 상태를 조회한다. "
                    + "진행 중·일시정지·종료·폐기를 모두 반환하며 세션과 업로드 예약은 한 시점의 값이다. "
                    + "조회는 생존 신호가 아니며 상태·마지막 신호·제어 권한을 바꾸지 않고 제어 비밀값을 반환하지 않는다. "
                    + "전사·문서 처리 상태는 반환하지 않는다"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "본인 녹음의 세션·업로드 상태",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = RecordingDetailResponse.class))),
            @ApiResponse(responseCode = "400", description = "워크스페이스 또는 녹음 ID 형식 오류",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "인증되지 않은 요청",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "워크스페이스 소속 없음 또는 녹음 시작자가 아님",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "워크스페이스 또는 해당 워크스페이스의 녹음 없음",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    RecordingDetailResponse findDetail(
            long workspaceId,
            @Parameter(description = "녹음 세션 ID", schema = @Schema(minimum = "1")) long recordingId,
            @Parameter(hidden = true) AuthenticatedMember authenticatedMember
    );
    // @formatter:on
}
