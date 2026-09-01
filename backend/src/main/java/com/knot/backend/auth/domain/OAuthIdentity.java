package com.knot.backend.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;

@Getter
@Entity
@Table(name = "oauth_identities", uniqueConstraints = {
        @UniqueConstraint(name = "uk_oauth_identity_provider_user", columnNames = {"provider", "provider_user_id"}),
        @UniqueConstraint(name = "uk_oauth_identity_member_provider", columnNames = {"member_id", "provider"})})
public class OAuthIdentity {
    private static final int MAX_PROVIDER_USER_ID_LENGTH = 255;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 20)
    private OAuthProvider provider;

    @Column(name = "provider_user_id", nullable = false, length = 255)
    private String providerUserId;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    protected OAuthIdentity() {}

    private OAuthIdentity(
            OAuthUser oauthUser,
            long memberId
    ) {
        if (oauthUser == null || memberId <= 0) {
            throw new AuthException(AuthErrorCode.INVALID_OAUTH_USER);
        }

        this.provider = oauthUser.getProvider();
        this.providerUserId = oauthUser.getExternalId();
        this.memberId = memberId;

        validate();
    }

    public static OAuthIdentity create(
            OAuthUser oauthUser,
            long memberId
    ) {
        return new OAuthIdentity(
                oauthUser,
                memberId
        );
    }

    private void validate() {
        if (provider == null || providerUserId == null || providerUserId.isBlank()
                || providerUserId.length() > MAX_PROVIDER_USER_ID_LENGTH) {
            throw new AuthException(AuthErrorCode.INVALID_OAUTH_USER);
        }

        if (memberId == null || memberId <= 0) {
            throw new AuthException(AuthErrorCode.INVALID_AUTHENTICATED_MEMBER);
        }

    }
}
