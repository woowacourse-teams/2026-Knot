package com.knot.backend.auth.infrastructure;

import com.knot.backend.auth.domain.AuthErrorCode;
import com.knot.backend.auth.domain.AuthException;
import com.knot.backend.auth.domain.AuthSession;
import com.knot.backend.auth.domain.AuthSessionRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class AuthSessionRepositoryImpl implements AuthSessionRepository {
    private final AuthSessionJpaRepository jpaRepository;

    @Override
    public AuthSession save(AuthSession session) {
        try {
            return jpaRepository.saveAndFlush(session);
        } catch (DataAccessException exception) {
            throw new AuthException(
                    AuthErrorCode.AUTHENTICATION_INTERNAL_ERROR,
                    exception
            );
        }
    }

    @Override
    public Optional<AuthSession> findByRefreshTokenHash(String refreshTokenHash) {
        try {
            return jpaRepository.findByRefreshTokenHash(refreshTokenHash);
        } catch (DataAccessException exception) {
            throw new AuthException(
                    AuthErrorCode.AUTHENTICATION_INTERNAL_ERROR,
                    exception
            );
        }
    }
}
