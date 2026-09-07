package com.knot.backend.auth.presentation;

import com.knot.backend.auth.application.AuthService;
import com.knot.backend.auth.application.dto.command.CompleteNicknameCommand;
import com.knot.backend.auth.domain.AuthenticatedMember;
import com.knot.backend.auth.infrastructure.jwt.BearerTokenResolver;
import com.knot.backend.auth.presentation.dto.request.CompleteNicknameRequest;
import com.knot.backend.auth.presentation.dto.response.AccessTokenResponse;
import com.knot.backend.auth.presentation.dto.response.AuthenticatedMemberResponse;
import com.knot.backend.global.config.JwtProperties;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "인증", description = "회원가입, 로그인, 리프레쉬, 로그아웃, 확인")
public class AuthController {
    private final AuthService authService;
    private final JwtProperties jwtProperties;

    @GetMapping("/me")
    public AuthenticatedMemberResponse me(@AuthenticationPrincipal AuthenticatedMember authenticatedMember) {
        return AuthenticatedMemberResponse.from(authenticatedMember);
    }

    /**
     * 신규 가입자의 닉네임을 등록하고 액세스 토큰을 발급한다.
     *
     * 온보딩 토큰은 액세스 토큰이 아니라 `JwtAuthenticationFilter`가 인증하지 못하므로 여기서 `Authorization`
     * 헤더를 직접 읽는다(기획서 5.1).
     */
    @PostMapping("/nickname")
    public AccessTokenResponse completeNicknameSetup(
            @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @Valid @RequestBody CompleteNicknameRequest request
    ) {
        String accessToken = authService.completeNicknameSetup(
                new CompleteNicknameCommand(
                        BearerTokenResolver.resolve(authorization),
                        request.nickname()
                )
        );

        return AccessTokenResponse.of(
                accessToken,
                jwtProperties.getExpiration()
        );
    }

    /**
     * 로그아웃 훅.
     *
     * 자격증명이 클라이언트 저장소에만 있으므로 실제 로그아웃은 클라이언트가 토큰을 지우는 것이다(기획서 5.1). 서버는 204만 돌려주며,
     * 2단계(`A6`) 디바이스 세션 폐기가 붙을 자리다.
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        return ResponseEntity.noContent()
                .build();
    }
}
