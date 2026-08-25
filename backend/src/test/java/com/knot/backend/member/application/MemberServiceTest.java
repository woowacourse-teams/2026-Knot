package com.knot.backend.member.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.knot.backend.auth.domain.OAuthUser;
import com.knot.backend.member.domain.Member;
import com.knot.backend.member.domain.MemberErrorCode;
import com.knot.backend.member.domain.MemberException;
import com.knot.backend.member.domain.MemberRepository;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MemberServiceTest {

    @Test
    @DisplayName("GitHub ID로 member를 조회한다")
    void findByGithubId_success() {
        // given
        MemberRepository repository = mock(MemberRepository.class);
        MemberService service = new MemberService(repository);
        Member member = Member.create(
                42L,
                "octocat",
                null
        );
        when(repository.findByGithubId(42L)).thenReturn(Optional.of(member));

        // when
        Optional<Member> result = service.findByGithubId(42L);

        // then
        assertThat(result).containsSame(member);
        verify(repository).findByGithubId(42L);
    }

    @Test
    @DisplayName("OAuth 사용자로 member를 생성한다")
    void create_success() {
        // given
        MemberRepository repository = mock(MemberRepository.class);
        MemberService service = new MemberService(repository);
        OAuthUser oauthUser = OAuthUser.of(
                42L,
                "octocat",
                null
        );
        Member savedMember = Member.create(
                oauthUser.getExternalId(),
                oauthUser.getNickname(),
                oauthUser.getProfileImageUrl()
        );
        when(repository.save(any(Member.class))).thenReturn(savedMember);

        // when
        Member result = service.create(oauthUser);

        // then
        assertThat(result).isSameAs(savedMember);
        verify(repository).save(any(Member.class));
    }

    @Test
    @DisplayName("member의 OAuth 프로필을 갱신한다")
    void updateProfile_success() {
        // given
        MemberRepository repository = mock(MemberRepository.class);
        MemberService service = new MemberService(repository);
        Member member = Member.create(
                42L,
                "old-name",
                null
        );
        OAuthUser updatedUser = OAuthUser.of(
                42L,
                "new-name",
                null
        );
        when(repository.save(member)).thenReturn(member);

        // when
        Member result = service.updateProfile(
                member,
                updatedUser
        );

        // then
        assertThat(result.getNickname()).isEqualTo("new-name");
        verify(repository).save(member);
    }

    @Test
    @DisplayName("GitHub 사용자가 처음 로그인하면 member를 생성한다")
    void login_success_newMember() {
        // given
        MemberRepository repository = mock(MemberRepository.class);
        MemberService service = new MemberService(repository);
        OAuthUser oauthUser = OAuthUser.of(
                42L,
                "octocat",
                "https://example.com/avatar"
        );
        Member savedMember = Member.create(
                oauthUser.getExternalId(),
                oauthUser.getNickname(),
                oauthUser.getProfileImageUrl()
        );
        when(repository.findByGithubId(42L)).thenReturn(Optional.of(savedMember));

        // when
        Member result = service.login(oauthUser);

        // then
        assertThat(result).isSameAs(savedMember);
        assertThat(result.getGithubId()).isEqualTo(42L);
        verify(repository).saveLoginProfile(any(Member.class));
    }

    @Test
    @DisplayName("기존 GitHub 사용자가 로그인하면 프로필 정보를 갱신한다")
    void login_success_existingMember() {
        // given
        MemberRepository repository = mock(MemberRepository.class);
        MemberService service = new MemberService(repository);
        OAuthUser updatedUser = OAuthUser.of(
                42L,
                "new-name",
                "https://example.com/new"
        );
        Member updatedMember = Member.create(
                updatedUser.getExternalId(),
                updatedUser.getNickname(),
                updatedUser.getProfileImageUrl()
        );
        when(repository.findByGithubId(42L)).thenReturn(Optional.of(updatedMember));

        // when
        Member result = service.login(updatedUser);

        // then
        assertThat(result).isSameAs(updatedMember);
        assertThat(result.getNickname()).isEqualTo("new-name");
        assertThat(result.getProfileImageUrl()).isEqualTo("https://example.com/new");
        verify(repository).saveLoginProfile(any(Member.class));
    }

    @Test
    @DisplayName("OAuth 사용자가 없으면 member 조회/생성을 진행하지 않는다")
    void login_failure_nullOAuthUser() {
        // given
        MemberRepository repository = mock(MemberRepository.class);
        MemberService service = new MemberService(repository);

        // when & then
        assertThatThrownBy(() -> service.login(null)).isInstanceOf(MemberException.class)
                .extracting(exception -> ((MemberException) exception).getErrorCode())
                .isEqualTo(MemberErrorCode.INVALID_MEMBER_DATA);
        verify(
                repository,
                never()
        ).saveLoginProfile(any(Member.class));
    }

    @Test
    @DisplayName("로그인 프로필 저장 후 member를 조회하지 못하면 커스텀 예외를 발생시킨다")
    void login_failure_memberNotFoundAfterSave() {
        // given
        MemberRepository repository = mock(MemberRepository.class);
        MemberService service = new MemberService(repository);
        OAuthUser oauthUser = OAuthUser.of(
                42L,
                "octocat",
                null
        );
        when(repository.findByGithubId(42L)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> service.login(oauthUser)).isInstanceOfSatisfying(
                MemberException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(MemberErrorCode.MEMBER_LOGIN_FAILED)
        );
    }
}
