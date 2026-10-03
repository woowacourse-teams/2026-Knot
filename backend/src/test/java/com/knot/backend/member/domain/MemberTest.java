package com.knot.backend.member.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

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
    @DisplayName("1자와 20자 한글·영어 닉네임 및 괄호·하이픈을 허용한다")
    void create_success_validNicknameBoundaries() {
        // given
        List<String> nicknames = List.of(
                "a",
                "가",
                "a".repeat(20),
                "가".repeat(20),
                "(knot)-user"
        );

        // when & then
        nicknames.forEach(
                nickname -> assertThat(
                        Member.create(
                                nickname,
                                null
                        )
                                .getNickname()
                ).isEqualTo(nickname)
        );
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

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " ", "name1", "user_name", "two words", "nickname!"})
    @DisplayName("허용되지 않은 닉네임 문자가 있으면 member 생성을 거부한다")
    void create_failure_invalidNicknameCharacters(String nickname) {
        // when & then
        assertThatThrownBy(
                () -> Member.create(
                        nickname,
                        null
                )
        ).isInstanceOf(MemberException.class);
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
