package com.knot.backend.auth.infrastructure;

import com.knot.backend.auth.domain.AuthErrorCode;
import com.knot.backend.auth.domain.AuthException;
import com.knot.backend.auth.domain.ConsumedRefreshToken;
import com.knot.backend.auth.domain.ConsumedRefreshTokenRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ConsumedRefreshTokenRepositoryImpl implements ConsumedRefreshTokenRepository {
    private final ConsumedRefreshTokenJpaRepository jpaRepository;

    @Override
    public ConsumedRefreshToken save(ConsumedRefreshToken consumedRefreshToken) {
        try {
            return jpaRepository.saveAndFlush(consumedRefreshToken);
        } catch (DataAccessException exception) {
            throw new AuthException(
                    AuthErrorCode.AUTHENTICATION_INTERNAL_ERROR,
                    exception
            );
        }
    }

    @Override
    public Optional<ConsumedRefreshToken> findByRefreshTokenHash(String refreshTokenHash) {
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
