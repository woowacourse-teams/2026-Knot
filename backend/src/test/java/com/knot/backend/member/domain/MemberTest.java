package com.knot.backend.member.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MemberTest {

    @Test
    @DisplayName("유효한 닉네임으로 member를 생성한다")
    void create_success() {
        // given

        // when
        Member member = Member.create(
                "octocat",
                "https://example.com/avatar"
        );

        // then
        assertThat(member.getNickname()).isEqualTo("octocat");
        assertThat(member.getProfileImageUrl()).isEqualTo("https://example.com/avatar");
    }

    @Test
    @DisplayName("닉네임이 비어 있으면 커스텀 예외를 발생시킨다")
    void create_failure_blankNickname() {
        // given

        // when
        Throwable thrown = catchThrowable(
                () -> Member.create(
                        " ",
                        null
                )
        );

        // then
        assertThat(thrown).isInstanceOf(MemberException.class)
                .extracting(exception -> ((MemberException) exception).getErrorCode())
                .isEqualTo(MemberErrorCode.INVALID_MEMBER_DATA);
    }

    @Test
    @DisplayName("닉네임이 길이 제한을 초과하면 커스텀 예외를 발생시킨다")
    void create_failure_overlongNickname() {
        // given

        // when
        Throwable thrown = catchThrowable(
                () -> Member.create(
                        "a".repeat(21),
                        null
                )
        );

        // then
        assertThat(thrown).isInstanceOf(MemberException.class);
    }

    @Test
    @DisplayName("member 프로필을 갱신한다")
    void updateProfile_success() {
        // given
        Member member = Member.create(
                "old-name",
                null
        );

        // when
        member.updateProfile(
                "new-name",
                "https://example.com/avatar"
        );

        // then
        assertThat(member.getNickname()).isEqualTo("new-name");
        assertThat(member.getProfileImageUrl()).isEqualTo("https://example.com/avatar");
    }

    @Test
    @DisplayName("프로필 갱신 닉네임이 비어 있으면 커스텀 예외를 발생시킨다")
    void updateProfile_failure_blankNickname() {
        // given
        Member member = Member.create(
                "octocat",
                null
        );

        // when
        Throwable thrown = catchThrowable(
                () -> member.updateProfile(
                        " ",
                        null
                )
        );

        // then
        assertThat(thrown).isInstanceOf(MemberException.class)
                .extracting(exception -> ((MemberException) exception).getErrorCode())
                .isEqualTo(MemberErrorCode.INVALID_MEMBER_DATA);
    }
}
