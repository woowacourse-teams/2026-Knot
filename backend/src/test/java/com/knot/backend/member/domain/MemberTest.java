package com.knot.backend.member.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MemberTest {

    @Test
    @DisplayName("GitHub ID 없이 member를 생성하면 커스텀 예외를 발생시킨다")
    void create_failure_nullGithubId() {
        // when & then
        assertThatThrownBy(
                () -> Member.create(
                        null,
                        "octocat",
                        null
                )
        ).isInstanceOf(MemberException.class)
                .extracting(exception -> ((MemberException) exception).getErrorCode())
                .isEqualTo(MemberErrorCode.INVALID_MEMBER_DATA);
    }

    @Test
    @DisplayName("다른 GitHub ID로 프로필을 갱신하면 커스텀 예외를 발생시킨다")
    void updateProfile_failure_githubIdChange() {
        // given
        Member member = Member.create(
                42L,
                "octocat",
                null
        );

        // when & then
        assertThatThrownBy(
                () -> member.updateProfile(
                        43L,
                        "other",
                        null
                )
        ).isInstanceOf(MemberException.class)
                .extracting(exception -> ((MemberException) exception).getErrorCode())
                .isEqualTo(MemberErrorCode.GITHUB_ID_CANNOT_BE_CHANGED);
    }

    @Test
    @DisplayName("유효한 member 정보로 member를 생성한다")
    void create_success() {
        // when
        Member member = Member.create(
                42L,
                "octocat",
                "https://example.com/avatar"
        );

        // then
        assertThat(member.getGithubId()).isEqualTo(42L);
        assertThat(member.getNickname()).isEqualTo("octocat");
        assertThat(member.getProfileImageUrl()).isEqualTo("https://example.com/avatar");
    }

    @Test
    @DisplayName("member 프로필 nickname이 비어 있으면 커스텀 예외를 발생시킨다")
    void updateProfile_failure_blankNickname() {
        // given
        Member member = Member.create(
                42L,
                "octocat",
                null
        );

        // when & then
        assertThatThrownBy(
                () -> member.updateProfile(
                        42L,
                        " ",
                        null
                )
        ).isInstanceOf(MemberException.class)
                .extracting(exception -> ((MemberException) exception).getErrorCode())
                .isEqualTo(MemberErrorCode.INVALID_MEMBER_DATA);
    }

    @Test
    @DisplayName("member 프로필을 같은 GitHub ID로 수정한다")
    void updateProfile_success() {
        // given
        Member member = Member.create(
                42L,
                "old-name",
                null
        );

        // when
        member.updateProfile(
                42L,
                "new-name",
                "https://example.com/avatar"
        );

        // then
        assertThat(member.getNickname()).isEqualTo("new-name");
        assertThat(member.getProfileImageUrl()).isEqualTo("https://example.com/avatar");
    }
}
