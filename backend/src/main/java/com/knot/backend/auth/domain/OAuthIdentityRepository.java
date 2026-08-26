package com.knot.backend.auth.domain;

import java.util.Optional;

public interface OAuthIdentityRepository {

    Optional<OAuthIdentity> findByProviderAndProviderUserId(
            OAuthProvider provider,
            String providerUserId
    );

    OAuthIdentity save(OAuthIdentity identity);
}
