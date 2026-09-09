package com.knot.backend.auth.infrastructure.oauth;

import com.knot.backend.auth.domain.DeviceLoginRequest;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.stereotype.Component;

/**
 * `GET /oauth2/authorization/github?client=desktop&…`의 데스크톱 파라미터를 인가 요청 attributes에 보관한다(기획서 5.2).
 *
 * 파라미터가 형식에 맞지 않으면 `AuthException`을 던지고, `OAuth2AuthorizationRequestRedirectFilter`가 이를 400으로 답한다.
 * `client` 파라미터가 없으면 웹 로그인이며 아무것도 더하지 않는다. attributes는 콜백에서 `StashingAuthorizationRequestRepository`가
 * 꺼내 성공·실패 핸들러에 넘긴다.
 */
@Component
public class DesktopOAuth2AuthorizationRequestResolver implements OAuth2AuthorizationRequestResolver {
    public static final String DEVICE_LOGIN_ATTRIBUTE = "knot.deviceLogin";

    private final OAuth2AuthorizationRequestResolver delegate;

    public DesktopOAuth2AuthorizationRequestResolver(ClientRegistrationRepository clientRegistrationRepository) {
        this(
                new DefaultOAuth2AuthorizationRequestResolver(
                        clientRegistrationRepository,
                        OAuth2AuthorizationRequestRedirectFilter.DEFAULT_AUTHORIZATION_REQUEST_BASE_URI
                )
        );
    }

    DesktopOAuth2AuthorizationRequestResolver(OAuth2AuthorizationRequestResolver delegate) {
        this.delegate = delegate;
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
        return attachDeviceLogin(
                request,
                delegate.resolve(request)
        );
    }

    @Override
    public OAuth2AuthorizationRequest resolve(
            HttpServletRequest request,
            String clientRegistrationId
    ) {
        return attachDeviceLogin(
                request,
                delegate.resolve(
                        request,
                        clientRegistrationId
                )
        );
    }

    private OAuth2AuthorizationRequest attachDeviceLogin(
            HttpServletRequest request,
            OAuth2AuthorizationRequest authorizationRequest
    ) {
        if (authorizationRequest == null) {
            return null;
        }
        String client = request.getParameter(DeviceLoginRequest.CLIENT_PARAMETER);
        if (client == null) {
            return authorizationRequest;
        }
        DeviceLoginRequest deviceLogin = parse(
                request,
                client
        );
        return OAuth2AuthorizationRequest.from(authorizationRequest)
                .attributes(
                        attributes -> attributes.put(
                                DEVICE_LOGIN_ATTRIBUTE,
                                deviceLogin
                        )
                )
                .build();
    }

    private DeviceLoginRequest parse(
            HttpServletRequest request,
            String client
    ) {
        if (!DeviceLoginRequest.DESKTOP_CLIENT.equals(client)) {
            throw new com.knot.backend.auth.domain.AuthException(
                    com.knot.backend.auth.domain.AuthErrorCode.DEVICE_LOGIN_REQUEST_INVALID
            );
        }
        return DeviceLoginRequest.of(
                request.getParameter(DeviceLoginRequest.CODE_CHALLENGE_PARAMETER),
                request.getParameter(DeviceLoginRequest.CODE_CHALLENGE_METHOD_PARAMETER),
                request.getParameter(DeviceLoginRequest.STATE_PARAMETER),
                request.getParameter(DeviceLoginRequest.RETURN_PARAMETER)
        );
    }
}
