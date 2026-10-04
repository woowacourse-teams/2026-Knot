package com.knot.backend.auth.application;

import com.knot.backend.auth.application.dto.result.AuthRefreshResult;
import com.knot.backend.auth.domain.AuthErrorCode;
import com.knot.backend.auth.domain.AuthException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthRefreshService {
    private final AuthSessionRefreshService authSessionRefreshService;

    public AuthRefreshResult refresh(String refreshToken) {
        return authSessionRefreshService.refresh(refreshToken)
                .orElseThrow(() -> new AuthException(AuthErrorCode.UNAUTHENTICATED));
    }
}
