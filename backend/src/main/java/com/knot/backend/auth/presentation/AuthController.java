package com.knot.backend.auth.presentation;

import com.knot.backend.auth.application.AuthService;
import com.knot.backend.auth.domain.AuthenticatedMember;
import com.knot.backend.auth.presentation.dto.request.CompleteNicknameRequest;
import com.knot.backend.auth.presentation.dto.response.AuthenticatedMemberResponse;
import com.knot.backend.global.config.JwtProperties;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;
    private final JwtProperties jwtProperties;

    @GetMapping("/me")
    public AuthenticatedMemberResponse me(
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember
    ) {
        return AuthenticatedMemberResponse.from(authenticatedMember);
    }

    @PostMapping("/nickname")
    public ResponseEntity<Void> completeNickname(
            @CookieValue(name = "${auth.jwt.nickname-cookie-name}", required = false) String nicknameToken,
            @Valid @RequestBody CompleteNicknameRequest request,
            HttpServletResponse response
    ) {
        String accessToken = authService.completeNickname(
                nicknameToken,
                request.nickname()
        );

        addCookie(
                response,
                jwtProperties.getCookieName(),
                accessToken,
                jwtProperties.getExpiration()
        );
        expireCookie(
                response,
                jwtProperties.getNicknameCookieName()
        );

        return ResponseEntity.noContent()
                .build();
    }

    private void addCookie(
            HttpServletResponse response,
            String name,
            String value,
            Duration maxAge
    ) {
        ResponseCookie cookie = ResponseCookie.from(
                name,
                value
        )
                .httpOnly(true)
                .secure(jwtProperties.isSecure())
                .sameSite("Lax")
                .path("/")
                .maxAge(maxAge)
                .build();

        response.addHeader(
                HttpHeaders.SET_COOKIE,
                cookie.toString()
        );
    }

    private void expireCookie(
            HttpServletResponse response,
            String name
    ) {
        addCookie(
                response,
                name,
                "",
                Duration.ZERO
        );
    }
}
