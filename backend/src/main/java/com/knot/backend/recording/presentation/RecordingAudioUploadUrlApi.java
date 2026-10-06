package com.knot.backend.recording.presentation;

import static com.knot.backend.global.config.OpenApiConfig.ACCESS_TOKEN_COOKIE;

import com.knot.backend.auth.domain.AuthenticatedMember;
import com.knot.backend.global.config.OpenApiConfig;
import com.knot.backend.global.response.ErrorResponse;
import com.knot.backend.recording.presentation.dto.request.RecordingAudioUploadUrlRequest;
import com.knot.backend.recording.presentation.dto.response.RecordingAudioUploadUrlResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

@Tag(name = "녹음", description = "본인 녹음 세션")
@SecurityRequirement(name = ACCESS_TOKEN_COOKIE)
public interface RecordingAudioUploadUrlApi {

    // @formatter:off
    @Operation(
            summary = "최종 오디오 업로드 URL 발급",
            description = "종료된 녹음의 최종 오디오 파일을 객체 저장소에 직접 PUT할 URL을 발급한다. 녹음 시작자면 어느 탭에서든 요청할 수 있다. "
                    + "PUT 요청의 Content-Type과 Content-Length는 이 요청과 같아야 한다. 아직 완료되지 않은 예약은 같은 uploadId로 URL만 다시 발급한다",
            parameters = @Parameter(
                    name = OpenApiConfig.CSRF_TOKEN_HEADER_NAME,
                    in = ParameterIn.HEADER,
                    required = true,
                    schema = @Schema(type = "string"),
                    description = "CSRF 토큰"
            )
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "새 업로드 예약과 URL 발급",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = RecordingAudioUploadUrlResponse.class))),
            @ApiResponse(responseCode = "200", description = "아직 완료되지 않은 기존 예약의 URL 재발급",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = RecordingAudioUploadUrlResponse.class))),
            @ApiResponse(responseCode = "400", description = "요청 본문 형식 오류 또는 허용되지 않는 형식·크기",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "인증되지 않은 요청",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "워크스페이스 소속 없음, 녹음 시작자가 아님 또는 CSRF 검증 실패",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "워크스페이스 또는 해당 워크스페이스의 녹음 없음",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "종료 전·폐기된 녹음 또는 업로드가 이미 완료된 녹음",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "오디오 저장소가 설정되지 않음",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<RecordingAudioUploadUrlResponse> issue(
            long workspaceId,
            @Parameter(description = "녹음 세션 ID", schema = @Schema(minimum = "1")) long recordingId,
            @Valid RecordingAudioUploadUrlRequest request,
            @Parameter(hidden = true) AuthenticatedMember authenticatedMember
    );
    // @formatter:on
}
