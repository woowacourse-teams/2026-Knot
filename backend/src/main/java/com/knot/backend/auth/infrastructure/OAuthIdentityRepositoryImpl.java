package com.knot.backend.auth.infrastructure;

import com.knot.backend.auth.domain.AuthErrorCode;
import com.knot.backend.auth.domain.AuthException;
import com.knot.backend.auth.domain.OAuthIdentity;
import com.knot.backend.auth.domain.OAuthIdentityRepository;
import com.knot.backend.auth.domain.OAuthProvider;
import java.sql.SQLException;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class OAuthIdentityRepositoryImpl implements OAuthIdentityRepository {
    private static final String UNIQUE_VIOLATION_SQL_STATE = "23505";

    private final OAuthIdentityJpaRepository oauthIdentityJpaRepository;

    @Override
    public Optional<OAuthIdentity> findByProviderAndProviderUserId(
            OAuthProvider provider,
            String providerUserId
    ) {
        return oauthIdentityJpaRepository.findByProviderAndProviderUserId(
                provider,
                providerUserId
        );
    }

    @Override
    public OAuthIdentity save(OAuthIdentity identity) {
        try {
            return oauthIdentityJpaRepository.saveAndFlush(identity);
        } catch (DataIntegrityViolationException exception) {
            if (!isUniqueConstraintViolation(exception)) {
                throw exception;
            }
            throw new AuthException(
                    AuthErrorCode.NICKNAME_SETUP_ALREADY_COMPLETED,
                    exception
            );
        }
    }

    private boolean isUniqueConstraintViolation(Throwable exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (SQLException.class.isInstance(cause) && UNIQUE_VIOLATION_SQL_STATE.equals(
                    SQLException.class.cast(cause)
                            .getSQLState()
            )) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }
}
