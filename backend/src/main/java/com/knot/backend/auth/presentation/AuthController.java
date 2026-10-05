package com.knot.backend.auth.presentation;

import com.knot.backend.auth.application.AuthService;
import com.knot.backend.auth.application.AuthRefreshService;
import com.knot.backend.auth.application.dto.command.CompleteNicknameCommand;
import com.knot.backend.auth.application.dto.result.AuthRefreshResult;
import com.knot.backend.auth.application.dto.result.AuthLoginResult;
import com.knot.backend.auth.domain.AuthenticatedMember;
import com.knot.backend.auth.presentation.dto.request.CompleteNicknameRequest;
import com.knot.backend.auth.presentation.dto.response.AuthenticatedMemberResponse;
import com.knot.backend.auth.presentation.dto.response.CsrfTokenResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
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
public class AuthController implements AuthApi {
    private final AuthService authService;
    private final AuthRefreshService authRefreshService;
    private final AuthCookieManager authCookieManager;

    @Override
    @GetMapping("/me")
    public AuthenticatedMemberResponse me(@AuthenticationPrincipal AuthenticatedMember authenticatedMember) {
        return AuthenticatedMemberResponse.from(authenticatedMember);
    }

    @Override
    @GetMapping("/csrf")
    public CsrfTokenResponse csrf(CsrfToken csrfToken) {
        return new CsrfTokenResponse(csrfToken.getToken());
    }

    @Override
    @PostMapping("/refresh")
    public ResponseEntity<Void> refresh(
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        AuthRefreshResult result = authRefreshService.refresh(authCookieManager.findRefreshToken(request));
        authCookieManager.addAccessToken(
                response,
                result.accessToken()
        );
        authCookieManager.addRefreshToken(
                response,
                result.refreshToken(),
                result.refreshMaxAge()
        );
        return ResponseEntity.noContent()
                .build();
    }

    @Override
    @PostMapping("/nickname")
    public ResponseEntity<Void> completeNicknameSetup(
            @CookieValue(name = "${auth.jwt.nickname-cookie-name}", required = false) String nicknameToken,
            @Valid @RequestBody CompleteNicknameRequest request,
            HttpServletResponse response
    ) {
        AuthLoginResult result = authService.completeNicknameSetup(
                new CompleteNicknameCommand(
                        nicknameToken,
                        request.nickname()
                )
        );

        authCookieManager.addAccessToken(
                response,
                result.token()
        );
        authCookieManager.addRefreshToken(
                response,
                result.refreshToken(),
                result.refreshMaxAge()
        );
        authCookieManager.expireNicknameToken(response);

        return ResponseEntity.noContent()
                .build();
    }
}
