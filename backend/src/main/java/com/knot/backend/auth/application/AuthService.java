package com.knot.backend.auth.application;

import com.knot.backend.auth.domain.AuthErrorCode;
import com.knot.backend.auth.domain.AuthException;
import com.knot.backend.auth.domain.AuthTokenProvider;
import com.knot.backend.auth.domain.OAuthUser;
import com.knot.backend.member.application.MemberService;
import com.knot.backend.member.domain.MemberException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final MemberService memberService;
    private final AuthTokenProvider authTokenProvider;

    public String login(OAuthUser oauthUser) {
        if (oauthUser == null) {
            throw new AuthException(AuthErrorCode.INVALID_OAUTH_USER);
        }

        try {
            return authTokenProvider.issue(memberService.login(oauthUser));
        } catch (MemberException exception) {
            throw new AuthException(
                    AuthErrorCode.OAUTH_AUTHENTICATION_FAILED,
                    exception
            );
        } catch (AuthException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new AuthException(
                    AuthErrorCode.AUTHENTICATION_INTERNAL_ERROR,
                    exception
            );
        }
    }
}
