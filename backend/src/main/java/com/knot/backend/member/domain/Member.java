package com.knot.backend.member.domain;

import com.knot.backend.auth.domain.OAuthUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;

@Getter
@Entity
@Table(name = "member", uniqueConstraints = @UniqueConstraint(name = "uk_member_github_id", columnNames = "github_id"))
public class Member {

    private static final int MAX_NICKNAME_LENGTH = 20;
    private static final int MAX_PROFILE_IMAGE_URL_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;

    @Column(name = "github_id", nullable = false, unique = true) private long githubId;

    @Column(name = "nickname", nullable = false, length = 20) private String nickname;

    @Column(name = "profile_image_url", length = 500) private String profileImageUrl;

    protected Member() {}

    private Member(
            long githubId,
            String nickname,
            String profileImageUrl
    ) {
        validate(
                githubId,
                nickname,
                profileImageUrl
        );
        this.githubId = githubId;
        this.nickname = nickname;
        this.profileImageUrl = profileImageUrl;
    }

    public static Member create(OAuthUser oauthUser) {
        if (oauthUser == null) {
            throw new MemberException(MemberErrorCode.INVALID_MEMBER_DATA);
        }
        return new Member(
                oauthUser.getExternalId(),
                oauthUser.getNickname(),
                oauthUser.getProfileImageUrl()
        );
    }

    public void updateProfile(OAuthUser oauthUser) {
        if (oauthUser == null) {
            throw new MemberException(MemberErrorCode.INVALID_MEMBER_DATA);
        }
        if (githubId != oauthUser.getExternalId()) {
            throw new MemberException(MemberErrorCode.GITHUB_ID_CANNOT_BE_CHANGED);
        }
        this.nickname = oauthUser.getNickname();
        this.profileImageUrl = oauthUser.getProfileImageUrl();
    }

    private static void validate(
            long githubId,
            String nickname,
            String profileImageUrl
    ) {
        if (githubId <= 0) {
            throw new MemberException(MemberErrorCode.INVALID_MEMBER_DATA);
        }
        if (nickname == null || nickname.isBlank() || nickname.length() > MAX_NICKNAME_LENGTH) {
            throw new MemberException(MemberErrorCode.INVALID_MEMBER_DATA);
        }
        if (profileImageUrl != null && (profileImageUrl.isBlank()
                || profileImageUrl.length() > MAX_PROFILE_IMAGE_URL_LENGTH)) {
            throw new MemberException(MemberErrorCode.INVALID_MEMBER_DATA);
        }
    }
}
