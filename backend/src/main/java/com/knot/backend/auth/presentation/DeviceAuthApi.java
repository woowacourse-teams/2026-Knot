package com.knot.backend.auth.presentation;

import static com.knot.backend.global.config.OpenApiConfig.BEARER_AUTH;

import com.knot.backend.auth.domain.AuthenticatedMember;
import com.knot.backend.auth.presentation.dto.request.DeviceTokenRequest;
import com.knot.backend.auth.presentation.dto.request.RefreshTokenRequest;
import com.knot.backend.auth.presentation.dto.request.RevokeTokenRequest;
import com.knot.backend.auth.presentation.dto.response.DeviceSessionListResponse;
import com.knot.backend.auth.presentation.dto.response.DeviceTokenResponse;
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

@Tag(name = "디바이스 인증", description = "데스크톱 앱의 시스템 브라우저 로그인·디바이스 토큰·기기 세션(기획서 5.2)")
public interface DeviceAuthApi {

    // @formatter:off
    @Operation(
            summary = "디바이스 코드 교환",
            description = "시스템 브라우저 로그인 콜백으로 받은 일회용 코드와 PKCE code_verifier를 액세스·리프레시 토큰으로 바꾼다. "
                    + "코드는 120초 안에 한 번만 쓸 수 있고, 이미 쓴 코드가 다시 오면 그 코드로 만든 세션을 폐기한다. "
                    + "닉네임을 아직 정하지 않은 사용자면 requiresNickname=true와 온보딩 토큰만 돌려준다"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "발급 완료",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = DeviceTokenResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "코드 만료·재사용·verifier 불일치(DEVICE_CODE_INVALID) 또는 요청 형식 오류(VALIDATION_ERROR)",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    ResponseEntity<DeviceTokenResponse> exchange(DeviceTokenRequest request);

    @Operation(
            summary = "디바이스 토큰 갱신",
            description = "리프레시 토큰을 새 액세스·리프레시 쌍으로 바꾼다(rotation). 이전 리프레시 토큰은 즉시 무효가 되고, "
                    + "이미 바꾼 토큰이 다시 오면 탈취로 보고 세션 전체를 폐기한다"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "갱신 완료",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = DeviceTokenResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "요청 형식 오류(VALIDATION_ERROR)",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "모르는·만료된·재사용된·폐기된 리프레시 토큰(REFRESH_TOKEN_INVALID)",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    ResponseEntity<DeviceTokenResponse> refresh(RefreshTokenRequest request);

    @Operation(
            summary = "디바이스 토큰 폐기",
            description = "본문의 리프레시 토큰이 속한 세션, 또는 본문이 없으면 Bearer 액세스 토큰의 세션을 폐기한다. "
                    + "RFC 7009대로 모르는 토큰이어도 200이다"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "폐기 완료(또는 이미 무효)")
    })
    ResponseEntity<Void> revoke(
            RevokeTokenRequest request,
            @Parameter(hidden = true) AuthenticatedMember authenticatedMember
    );

    @Operation(
            summary = "기기 세션 목록",
            description = "내 기기 로그인 목록. 웹 액세스 토큰으로 조회하면 current가 모두 false다"
    )
    @SecurityRequirement(name = BEARER_AUTH)
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 완료",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = DeviceSessionListResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증되지 않은 요청",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    ResponseEntity<DeviceSessionListResponse> sessions(@Parameter(hidden = true) AuthenticatedMember authenticatedMember);

    @Operation(
            summary = "기기 세션 폐기(원격 로그아웃)",
            description = "내 기기 세션 하나를 폐기한다. 그 세션의 액세스 토큰은 만료 전이라도 즉시 401이 된다. "
                    + "현재 세션을 지우면 이 요청의 토큰도 함께 무효가 된다"
    )
    @SecurityRequirement(name = BEARER_AUTH)
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "폐기 완료"),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증되지 않은 요청",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "내 세션이 아니거나 이미 폐기됨(DEVICE_SESSION_NOT_FOUND)",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    ResponseEntity<Void> deleteSession(
            @Parameter(description = "세션 ID", in = ParameterIn.PATH, example = "1", required = true) long sessionId,
            @Parameter(hidden = true) AuthenticatedMember authenticatedMember
    );
    // @formatter:on
}
