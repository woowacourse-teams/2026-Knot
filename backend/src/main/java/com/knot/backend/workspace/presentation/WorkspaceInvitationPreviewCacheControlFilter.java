package com.knot.backend.workspace.presentation;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Order(Ordered.HIGHEST_PRECEDENCE)
@Component
public class WorkspaceInvitationPreviewCacheControlFilter extends OncePerRequestFilter {
    private static final String PREVIEW_PATH_PREFIX = "/invitations/";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        if (isPreviewPath(request)) {
            response.setHeader(
                    HttpHeaders.CACHE_CONTROL,
                    "no-store"
            );
        }
        filterChain.doFilter(
                request,
                response
        );
    }

    private boolean isPreviewPath(HttpServletRequest request) {
        String applicationPath = request.getRequestURI()
                .substring(
                        request.getContextPath()
                                .length()
                );
        return applicationPath.startsWith(PREVIEW_PATH_PREFIX);
    }
}
