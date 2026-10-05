package com.knot.backend.auth.presentation;

import static com.knot.backend.global.config.OpenApiConfig.ACCESS_TOKEN_COOKIE;
import static com.knot.backend.global.config.OpenApiConfig.CSRF_TOKEN_HEADER_NAME;

import com.knot.backend.auth.domain.AuthenticatedMember;
import com.knot.backend.auth.presentation.dto.request.CompleteNicknameRequest;
import com.knot.backend.auth.presentation.dto.response.AuthenticatedMemberResponse;
import com.knot.backend.auth.presentation.dto.response.CsrfTokenResponse;
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
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;

@Tag(name = "인증", description = "회원가입, 로그인, 리프레쉬, 로그아웃, 확인")
public interface AuthApi {

    // @formatter:off
    @Operation(summary = "내 회원 정보 조회")
    @SecurityRequirement(name = ACCESS_TOKEN_COOKIE)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "현재 회원 정보",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = AuthenticatedMemberResponse.class))),
            @ApiResponse(responseCode = "401", description = "인증되지 않은 요청",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    AuthenticatedMemberResponse me(@Parameter(hidden = true) AuthenticatedMember authenticatedMember);

    @Operation(summary = "CSRF 토큰 조회", description = "로그인 없이 조회할 수 있습니다. 변경 요청의 X-XSRF-TOKEN 헤더에 응답 토큰을 전달합니다.")
    @ApiResponse(responseCode = "200", description = "CSRF 토큰",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = CsrfTokenResponse.class)))
    CsrfTokenResponse csrf(@Parameter(hidden = true) CsrfToken csrfToken);

    @Operation(summary = "인증 토큰 재발급", description = "Access token 없이 refresh 쿠키와 CSRF 토큰으로 재발급합니다.",
            parameters = {
                    @Parameter(name = "__Host-KNOT_REFRESH_TOKEN", in = ParameterIn.COOKIE, required = true,
                            schema = @Schema(type = "string"), description = "현재 로그인 세션의 refresh token"),
                    @Parameter(name = CSRF_TOKEN_HEADER_NAME, in = ParameterIn.HEADER, required = true,
                            schema = @Schema(type = "string"), description = "CSRF 토큰")
            })
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "새 access·refresh 쿠키 발급",
                    headers = @Header(name = "Set-Cookie", description = "HttpOnly·Secure·Path=/ 쿠키 두 개 발급; Domain 없음",
                            schema = @Schema(type = "string"))),
            @ApiResponse(responseCode = "401", description = "UNAUTHENTICATED: 재발급 불가",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "CSRF_INVALID: CSRF 검증 실패",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<Void> refresh(
            @Parameter(hidden = true) HttpServletRequest request,
            @Parameter(hidden = true) HttpServletResponse response
    );

    @Operation(summary = "닉네임 설정", description = "온보딩 사용자의 회원 정보를 생성하고 access·refresh 쿠키를 발급합니다.",
            parameters = {
                    @Parameter(name = "KNOT_NICKNAME_TOKEN", in = ParameterIn.COOKIE, required = true,
                            schema = @Schema(type = "string"), description = "OAuth callback에서 발급한 온보딩 쿠키"),
                    @Parameter(name = CSRF_TOKEN_HEADER_NAME, in = ParameterIn.HEADER, required = true,
                            schema = @Schema(type = "string"), description = "CSRF 토큰")
            })
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "가입 완료 및 온보딩 쿠키 만료",
                    headers = @Header(name = "Set-Cookie", description = "Access·refresh 쿠키 발급 및 온보딩 쿠키 만료",
                            schema = @Schema(type = "string"))),
            @ApiResponse(responseCode = "400", description = "VALIDATION_ERROR 또는 INVALID_REQUEST_BODY: 닉네임·요청 본문 오류",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "INVALID_JWT: 온보딩 쿠키 누락·변조·만료",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "CSRF_INVALID: CSRF 검증 실패",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "NICKNAME_SETUP_ALREADY_COMPLETED: 가입 완료 계정",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<Void> completeNicknameSetup(
            @Parameter(hidden = true) String nicknameToken,
            CompleteNicknameRequest request,
            @Parameter(hidden = true) HttpServletResponse response
    );
    // @formatter:on
}
