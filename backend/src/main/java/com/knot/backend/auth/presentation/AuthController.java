package com.knot.backend.auth.presentation;

import com.knot.backend.auth.application.AuthService;
import com.knot.backend.auth.application.dto.command.CompleteNicknameCommand;
import com.knot.backend.auth.domain.AuthenticatedMember;
import com.knot.backend.auth.presentation.dto.request.CompleteNicknameRequest;
import com.knot.backend.auth.presentation.dto.response.AuthenticatedMemberResponse;
import com.knot.backend.auth.presentation.dto.response.CsrfTokenResponse;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "인증", description = "회원가입, 로그인, 리프레쉬, 로그아웃, 확인")
public class AuthController {
    private final AuthService authService;
    private final AuthCookieManager authCookieManager;

    @GetMapping("/me")
    public AuthenticatedMemberResponse me(@AuthenticationPrincipal AuthenticatedMember authenticatedMember) {
        return AuthenticatedMemberResponse.from(authenticatedMember);
    }

    @GetMapping("/csrf")
    public CsrfTokenResponse csrf(CsrfToken csrfToken) {
        return new CsrfTokenResponse(csrfToken.getToken());
    }

    @PostMapping("/nickname")
    public ResponseEntity<Void> completeNicknameSetup(
            @CookieValue(name = "${auth.jwt.nickname-cookie-name}", required = false) String nicknameToken,
            @Valid @RequestBody CompleteNicknameRequest request,
            HttpServletResponse response
    ) {
        String accessToken = authService.completeNicknameSetup(
                new CompleteNicknameCommand(
                        nicknameToken,
                        request.nickname()
                )
        );

        authCookieManager.addAccessToken(
                response,
                accessToken
        );
        authCookieManager.expireNicknameToken(response);

        return ResponseEntity.noContent()
                .build();
    }
}
