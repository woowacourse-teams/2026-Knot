package com.knot.backend.auth.presentation;

import com.knot.backend.auth.application.AuthService;
import com.knot.backend.auth.application.dto.command.CompleteNicknameCommand;
import com.knot.backend.auth.domain.AuthenticatedMember;
import com.knot.backend.auth.presentation.dto.request.CompleteNicknameRequest;
import com.knot.backend.auth.presentation.dto.response.AuthenticatedMemberResponse;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
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
    private final AuthCookieManager authCookieManager;

    @GetMapping("/me")
    public AuthenticatedMemberResponse me(
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember
    ) {
        return AuthenticatedMemberResponse.from(authenticatedMember);
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
