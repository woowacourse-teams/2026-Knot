package com.knot.backend.member.domain;

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
@Table(name = "members", uniqueConstraints = @UniqueConstraint(name = "uk_member_github_id", columnNames = "github_id"))
public class Member {
    private static final int MAX_NICKNAME_LENGTH = 20;
    private static final int MAX_PROFILE_IMAGE_URL_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "github_id", nullable = false, unique = true)
    private long githubId;

    @Column(name = "nickname", nullable = false, length = 20)
    private String nickname;

    @Column(name = "profile_image_url", length = 500)
    private String profileImageUrl;

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

    public static Member create(
            Long githubId,
            String nickname,
            String profileImageUrl
    ) {
        if (githubId == null) {
            throw new MemberException(MemberErrorCode.INVALID_MEMBER_DATA);
        }
        return new Member(
                githubId,
                nickname,
                profileImageUrl
        );
    }

    public void updateProfile(
            long githubId,
            String nickname,
            String profileImageUrl
    ) {
        if (this.githubId != githubId) {
            throw new MemberException(MemberErrorCode.GITHUB_ID_CANNOT_BE_CHANGED);
        }
        validate(
                githubId,
                nickname,
                profileImageUrl
        );
        this.nickname = nickname;
        this.profileImageUrl = profileImageUrl;
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
