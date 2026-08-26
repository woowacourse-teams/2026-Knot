package com.knot.backend.auth.infrastructure;

import com.knot.backend.auth.domain.AuthErrorCode;
import com.knot.backend.auth.domain.AuthException;
import com.knot.backend.auth.domain.OAuthIdentity;
import com.knot.backend.auth.domain.OAuthIdentityRepository;
import com.knot.backend.auth.domain.OAuthProvider;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class OAuthIdentityRepositoryImpl implements OAuthIdentityRepository {

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
            throw new AuthException(
                    AuthErrorCode.NICKNAME_SETUP_ALREADY_COMPLETED,
                    exception
            );
        }
    }
}
