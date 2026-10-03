package com.knot.backend.auth.infrastructure;

import com.knot.backend.auth.domain.AuthErrorCode;
import com.knot.backend.auth.domain.AuthException;
import com.knot.backend.auth.domain.RefreshTokenHistory;
import com.knot.backend.auth.domain.RefreshTokenHistoryRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class RefreshTokenHistoryRepositoryImpl implements RefreshTokenHistoryRepository {
    private final RefreshTokenHistoryJpaRepository jpaRepository;

    @Override
    public RefreshTokenHistory save(RefreshTokenHistory history) {
        try {
            return jpaRepository.saveAndFlush(history);
        } catch (DataAccessException exception) {
            throw new AuthException(
                    AuthErrorCode.AUTHENTICATION_INTERNAL_ERROR,
                    exception
            );
        }
    }

    @Override
    public Optional<RefreshTokenHistory> findByRefreshTokenHash(String refreshTokenHash) {
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
