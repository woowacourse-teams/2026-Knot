package com.knot.backend.auth.infrastructure.jwt;

import com.knot.backend.auth.application.DeviceAuthService;
import com.knot.backend.auth.domain.AuthException;
import com.knot.backend.auth.domain.AuthTokenProvider;
import com.knot.backend.auth.domain.AuthenticatedMember;
import com.knot.backend.member.application.MemberService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * `Authorization: Bearer` 액세스 토큰으로 요청을 인증한다(기획서 5.1).
 *
 * 토큰은 서명·만료만으로 유효하므로 회원이 사라진 뒤(탈퇴·DB 초기화)에도 통과한다. 그대로 두면 `/auth/me`가 200을 줘
 * 클라이언트가 로그인 상태로 남고 이후 요청이 404·500으로 흩어진다. 그래서 subject의 회원이 아직 있는지 요청마다 확인하고,
 * 없으면 인증하지 않아 진입점이 401을 주게 한다(로드맵 Q41). 클라이언트는 만료와 같은 경로로 토큰을 지운다.
 *
 * 데스크톱 `DEVICE_ACCESS` 토큰(`sid`)은 회원 확인 대신 기기 세션이 폐기되지 않았는지 본다(기획서 5.2 Spring Security 변경).
 * 세션 행이 회원을 참조하므로 회원 존재도 함께 보장된다.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final AuthTokenProvider authTokenProvider;
    private final MemberService memberService;
    private final DeviceAuthService deviceAuthService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        SecurityContextHolder.clearContext();
        try {
            String token = BearerTokenResolver.resolve(request.getHeader(HttpHeaders.AUTHORIZATION));
            if (token != null) {
                AuthenticatedMember principal = authTokenProvider.authenticate(token);
                if (isStillValid(principal)) {
                    UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                            principal,
                            null,
                            List.of(new SimpleGrantedAuthority("ROLE_USER"))
                    );
                    SecurityContextHolder.getContext()
                            .setAuthentication(authentication);
                }
            }
        } catch (AuthException ignored) {
        }

        filterChain.doFilter(
                request,
                response
        );
    }

    private boolean isStillValid(AuthenticatedMember principal) {
        if (principal.isDeviceSession()) {
            return deviceAuthService.isSessionActive(
                    principal.getDeviceSessionId(),
                    principal.getMemberId()
            );
        }
        return memberService.existsById(principal.getMemberId());
    }
}
