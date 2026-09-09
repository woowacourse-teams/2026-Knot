package com.knot.backend.auth.presentation;

import com.knot.backend.auth.application.DeviceAuthService;
import com.knot.backend.auth.application.dto.command.ExchangeDeviceCodeCommand;
import com.knot.backend.auth.domain.AuthenticatedMember;
import com.knot.backend.auth.presentation.dto.request.DeviceTokenRequest;
import com.knot.backend.auth.presentation.dto.request.RefreshTokenRequest;
import com.knot.backend.auth.presentation.dto.request.RevokeTokenRequest;
import com.knot.backend.auth.presentation.dto.response.DeviceSessionListResponse;
import com.knot.backend.auth.presentation.dto.response.DeviceTokenResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 데스크톱 2단계 인증 API(기획서 5.2 계약 표). 토큰 응답은 어디에도 캐시되지 않도록 `Cache-Control: no-store`를 붙인다.
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class DeviceAuthController implements DeviceAuthApi {
    private final DeviceAuthService deviceAuthService;

    @Override
    @PostMapping("/device/token")
    public ResponseEntity<DeviceTokenResponse> exchange(@Valid @RequestBody DeviceTokenRequest request) {
        return noStore(
                DeviceTokenResponse.from(
                        deviceAuthService.exchange(
                                new ExchangeDeviceCodeCommand(
                                        request.code(),
                                        request.codeVerifier(),
                                        request.device()
                                                .toDeviceInfo()
                                )
                        )
                )
        );
    }

    @Override
    @PostMapping("/device/refresh")
    public ResponseEntity<DeviceTokenResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return noStore(DeviceTokenResponse.from(deviceAuthService.refresh(request.refreshToken())));
    }

    @Override
    @PostMapping("/device/revoke")
    public ResponseEntity<Void> revoke(
            @Valid @RequestBody(required = false) RevokeTokenRequest request,
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember
    ) {
        if (request != null && request.refreshToken() != null && !request.refreshToken()
                .isBlank()) {
            deviceAuthService.revokeByRefreshToken(request.refreshToken());
        } else if (authenticatedMember != null && authenticatedMember.isDeviceSession()) {
            deviceAuthService.revokeSession(
                    authenticatedMember.getDeviceSessionId(),
                    authenticatedMember.getMemberId()
            );
        }
        return ResponseEntity.ok()
                .build();
    }

    @Override
    @GetMapping("/sessions")
    public ResponseEntity<DeviceSessionListResponse> sessions(
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember
    ) {
        return noStore(DeviceSessionListResponse.from(deviceAuthService.findSessions(authenticatedMember)));
    }

    @Override
    @DeleteMapping("/sessions/{sessionId}")
    public ResponseEntity<Void> deleteSession(
            @PathVariable long sessionId,
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember
    ) {
        deviceAuthService.deleteSession(
                sessionId,
                authenticatedMember.getMemberId()
        );
        return ResponseEntity.noContent()
                .build();
    }

    private static <T> ResponseEntity<T> noStore(T body) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(body);
    }
}
