package com.knot.backend.member.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

@Getter
@Entity
@Table(name = "members")
public class Member {
    private static final int MAX_NICKNAME_LENGTH = 20;
    private static final int MAX_PROFILE_IMAGE_URL_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nickname", nullable = false, length = 20)
    private String nickname;

    @Column(name = "profile_image_url", length = 500)
    private String profileImageUrl;

    protected Member() {}

    private Member(
            String nickname,
            String profileImageUrl
    ) {
        validate(
                nickname,
                profileImageUrl
        );

        this.nickname = nickname;
        this.profileImageUrl = profileImageUrl;
    }

    public static Member create(
            String nickname,
            String profileImageUrl
    ) {
        return new Member(
                nickname,
                profileImageUrl
        );
    }

    public void updateProfile(
            String nickname,
            String profileImageUrl
    ) {
        validate(
                nickname,
                profileImageUrl
        );

        this.nickname = nickname;
        this.profileImageUrl = profileImageUrl;
    }

    private static void validate(
            String nickname,
            String profileImageUrl
    ) {
        if (nickname == null
                || nickname.isBlank()
                || nickname.length() > MAX_NICKNAME_LENGTH) {
            throw new MemberException(MemberErrorCode.INVALID_MEMBER_DATA);
        }

        if (profileImageUrl != null
                && (profileImageUrl.isBlank()
                || profileImageUrl.length() > MAX_PROFILE_IMAGE_URL_LENGTH)) {
            throw new MemberException(MemberErrorCode.INVALID_MEMBER_DATA);
        }
    }
}
