package com.knot.backend.auth.presentation.handler;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.stereotype.Component;

@Component
public class AuthLogoutSuccessHandler implements LogoutSuccessHandler {
    public static final String LOGOUT_FAILURE_ATTRIBUTE = AuthLogoutSuccessHandler.class.getName() + ".failed";

    @Override
    public void onLogoutSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException {
        if (Boolean.TRUE.equals(request.getAttribute(LOGOUT_FAILURE_ATTRIBUTE))) {
            return;
        }
        response.setStatus(HttpServletResponse.SC_NO_CONTENT);
    }
}
