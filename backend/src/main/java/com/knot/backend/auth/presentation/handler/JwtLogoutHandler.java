package com.knot.backend.auth.presentation.handler;

import com.knot.backend.auth.presentation.AuthCookieManager;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JwtLogoutHandler implements LogoutHandler {
    private final AuthCookieManager authCookieManager;

    @Override
    public void logout(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) {
        authCookieManager.expireAccessToken(response);
        authCookieManager.expireNicknameToken(response);
    }
}
