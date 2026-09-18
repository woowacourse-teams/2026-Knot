package com.knot.backend.workspace.presentation;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.regex.Pattern;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Order(Ordered.HIGHEST_PRECEDENCE)
@Component
public class NotionPageTreeCacheControlFilter extends OncePerRequestFilter {
    private static final Pattern PAGE_TREE_PATH = Pattern.compile("^/api/v1/workspaces/[^/]+/notion-pages/tree$");

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        if (isPageTreePath(request)) {
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

    private boolean isPageTreePath(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        String contextPath = request.getContextPath();
        String applicationPath = requestUri.substring(contextPath.length());
        return PAGE_TREE_PATH.matcher(applicationPath)
                .matches();
    }
}
