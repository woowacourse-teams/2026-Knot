package com.knot.backend.auth.presentation;

import com.knot.backend.auth.domain.AuthenticatedMember;
import com.knot.backend.auth.presentation.dto.response.AuthenticatedMemberResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

    @GetMapping("/me")
    public AuthenticatedMemberResponse me(
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember) {
        return AuthenticatedMemberResponse.from(authenticatedMember);
    }
}
