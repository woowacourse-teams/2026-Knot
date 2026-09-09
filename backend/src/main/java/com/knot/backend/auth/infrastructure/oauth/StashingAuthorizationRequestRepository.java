package com.knot.backend.auth.infrastructure.oauth;

import com.knot.backend.auth.domain.DeviceLoginRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Optional;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.client.web.HttpSessionOAuth2AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.stereotype.Component;

/**
 * 콜백에서 지운 인가 요청을 같은 HTTP 요청의 attribute로 남겨, 성공·실패 핸들러가 데스크톱 로그인 파라미터를 읽게 한다.
 *
 * `OAuth2LoginAuthenticationFilter`는 인증을 시도하기 전에 저장소에서 인가 요청을 지우고, 핸들러에는 `Authentication`만
 * 넘긴다. 세션 저장은 기존 `HttpSessionOAuth2AuthorizationRequestRepository`에 그대로 맡긴다.
 */
@Component
public class StashingAuthorizationRequestRepository
        implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {
    static final String REMOVED_REQUEST_ATTRIBUTE = StashingAuthorizationRequestRepository.class.getName()
            + ".REMOVED";

    private final AuthorizationRequestRepository<OAuth2AuthorizationRequest> delegate;

    public StashingAuthorizationRequestRepository() {
        this(new HttpSessionOAuth2AuthorizationRequestRepository());
    }

    StashingAuthorizationRequestRepository(AuthorizationRequestRepository<OAuth2AuthorizationRequest> delegate) {
        this.delegate = delegate;
    }

    @Override
    public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
        return delegate.loadAuthorizationRequest(request);
    }

    @Override
    public void saveAuthorizationRequest(
            OAuth2AuthorizationRequest authorizationRequest,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        delegate.saveAuthorizationRequest(
                authorizationRequest,
                request,
                response
        );
    }

    @Override
    public OAuth2AuthorizationRequest removeAuthorizationRequest(
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        OAuth2AuthorizationRequest removed = delegate.removeAuthorizationRequest(
                request,
                response
        );
        if (removed != null) {
            request.setAttribute(
                    REMOVED_REQUEST_ATTRIBUTE,
                    removed
            );
        }
        return removed;
    }

    /** 콜백 요청에서 데스크톱 로그인 파라미터를 꺼낸다. 웹 로그인이면 비어 있다 */
    public static Optional<DeviceLoginRequest> findDeviceLogin(HttpServletRequest request) {
        if (request == null) {
            return Optional.empty();
        }
        Object removed = request.getAttribute(REMOVED_REQUEST_ATTRIBUTE);
        if (!(removed instanceof OAuth2AuthorizationRequest authorizationRequest)) {
            return Optional.empty();
        }
        Object deviceLogin = authorizationRequest.getAttribute(
                DesktopOAuth2AuthorizationRequestResolver.DEVICE_LOGIN_ATTRIBUTE
        );
        if (deviceLogin instanceof DeviceLoginRequest deviceLoginRequest) {
            return Optional.of(deviceLoginRequest);
        }
        return Optional.empty();
    }
}
