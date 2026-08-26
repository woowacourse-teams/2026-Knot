package com.knot.backend.member.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.knot.backend.member.domain.Member;
import com.knot.backend.member.domain.MemberErrorCode;
import com.knot.backend.member.domain.MemberException;
import com.knot.backend.member.domain.MemberRepository;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MemberServiceTest {

    @Test
    @DisplayName("member ID로 member를 조회한다")
    void findById_success() {
        // given
        MemberRepository repository = mock(MemberRepository.class);
        MemberService service = new MemberService(repository);
        Member member = Member.create(
                "octocat",
                null
        );
        when(repository.findById(1L)).thenReturn(Optional.of(member));

        // when
        Optional<Member> result = service.findById(1L);

        // then
        assertThat(result).containsSame(member);
        verify(repository).findById(1L);
    }

    @Test
    @DisplayName("member ID가 유효하지 않으면 커스텀 예외를 발생시킨다")
    void findById_failure_invalidMemberId() {
        // given
        MemberRepository repository = mock(MemberRepository.class);
        MemberService service = new MemberService(repository);

        // when & then
        assertThatThrownBy(() -> service.findById(0L)).isInstanceOfSatisfying(
                MemberException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(MemberErrorCode.INVALID_MEMBER_DATA)
        );
    }

    @Test
    @DisplayName("닉네임으로 member를 생성하고 저장한다")
    void create_success() {
        // given
        MemberRepository repository = mock(MemberRepository.class);
        MemberService service = new MemberService(repository);
        Member savedMember = Member.create(
                "octocat",
                "https://example.com/avatar"
        );
        when(repository.save(any(Member.class))).thenReturn(savedMember);

        // when
        Member result = service.create(
                "octocat",
                "https://example.com/avatar"
        );

        // then
        assertThat(result).isSameAs(savedMember);
        verify(repository).save(any(Member.class));
    }

    @Test
    @DisplayName("member 프로필을 갱신하고 저장한다")
    void updateProfile_success() {
        // given
        MemberRepository repository = mock(MemberRepository.class);
        MemberService service = new MemberService(repository);
        Member member = Member.create(
                "old-name",
                null
        );
        when(repository.save(member)).thenReturn(member);

        // when
        Member result = service.updateProfile(
                member,
                "new-name",
                "https://example.com/avatar"
        );

        // then
        assertThat(result).isSameAs(member);
        assertThat(result.getNickname()).isEqualTo("new-name");
        verify(repository).save(member);
    }

    @Test
    @DisplayName("갱신할 member가 없으면 커스텀 예외를 발생시킨다")
    void updateProfile_failure_nullMember() {
        // given
        MemberService service = new MemberService(mock(MemberRepository.class));

        // when & then
        assertThatThrownBy(
                () -> service.updateProfile(
                        null,
                        "new-name",
                        null
                )
        ).isInstanceOfSatisfying(
                MemberException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(MemberErrorCode.INVALID_MEMBER_DATA)
        );
    }
}
