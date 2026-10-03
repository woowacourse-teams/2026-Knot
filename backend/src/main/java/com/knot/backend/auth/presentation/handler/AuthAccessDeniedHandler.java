package com.knot.backend.auth.presentation.handler;

import com.knot.backend.auth.domain.AuthErrorCode;
import com.knot.backend.global.exception.CommonErrorCode;
import com.knot.backend.global.exception.ErrorCode;
import com.knot.backend.global.response.ErrorResponse;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class AuthAccessDeniedHandler implements AccessDeniedHandler {
    private static final String NICKNAME_SETUP_PATH = "/api/v1/auth/nickname";

    private final ObjectMapper objectMapper;

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException
    ) throws IOException, ServletException {
        ErrorCode errorCode = request.getRequestURI()
                .endsWith(NICKNAME_SETUP_PATH) ? AuthErrorCode.CSRF_INVALID : CommonErrorCode.FORBIDDEN;
        writeErrorResponse(
                response,
                errorCode
        );
    }

    private void writeErrorResponse(
            HttpServletResponse response,
            ErrorCode errorCode
    ) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(
                response.getWriter(),
                new ErrorResponse(errorCode)
        );
    }
}
