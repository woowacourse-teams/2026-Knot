package com.knot.backend.auth.application;

import com.knot.backend.auth.domain.AuthErrorCode;
import com.knot.backend.auth.domain.AuthException;
import com.knot.backend.auth.domain.OAuthUser;
import com.knot.backend.auth.infrastructure.jwt.JwtProvider;
import com.knot.backend.member.application.MemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final MemberService memberService;
    private final JwtProvider jwtProvider;

    public String login(OAuthUser oauthUser) {
        if (oauthUser == null) {
            throw new AuthException(AuthErrorCode.INVALID_OAUTH_USER);
        }

        try {
            return jwtProvider.issue(memberService.login(oauthUser));
        } catch (AuthException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new AuthException(
                    AuthErrorCode.OAUTH_AUTHENTICATION_FAILED,
                    exception
            );
        }
    }
}
