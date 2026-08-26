package com.knot.backend.auth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.knot.backend.auth.domain.AuthErrorCode;
import com.knot.backend.auth.domain.AuthException;
import com.knot.backend.auth.domain.OAuthIdentity;
import com.knot.backend.auth.domain.OAuthIdentityRepository;
import com.knot.backend.auth.domain.OAuthProvider;
import com.knot.backend.auth.domain.OAuthUser;
import com.knot.backend.member.domain.Member;
import com.knot.backend.member.domain.MemberRepository;
import com.knot.backend.testsupport.TestApplicationProperties;
import com.knot.backend.testsupport.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.TestConstructor.AutowireMode;

@Tag("integration")
@Import(TestcontainersConfiguration.class)
@TestApplicationProperties
@SpringBootTest
@TestConstructor(autowireMode = AutowireMode.ALL)
class OAuthIdentityRepositoryTest {
    private final JdbcTemplate jdbcTemplate;
    private final MemberRepository memberRepository;
    private final OAuthIdentityRepository oauthIdentityRepository;

    OAuthIdentityRepositoryTest(
            JdbcTemplate jdbcTemplate,
            MemberRepository memberRepository,
            OAuthIdentityRepository oauthIdentityRepository
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.memberRepository = memberRepository;
        this.oauthIdentityRepository = oauthIdentityRepository;
    }

    @BeforeEach
    void clearTables() {
        jdbcTemplate.update("TRUNCATE TABLE oauth_identities, members RESTART IDENTITY");
    }

    @Test
    @DisplayName("OAuth identity를 저장하고 provider와 외부 ID로 조회한다")
    void saveAndFind_success() {
        // given
        Member member = memberRepository.save(
                Member.create(
                        "octocat",
                        null
                )
        );
        OAuthUser oauthUser = OAuthUser.of(
                OAuthProvider.GITHUB,
                "42",
                null
        );
        OAuthIdentity identity = OAuthIdentity.create(
                oauthUser,
                member.getId()
        );

        // when
        OAuthIdentity savedIdentity = oauthIdentityRepository.save(identity);

        // then
        assertThat(
                oauthIdentityRepository.findByProviderAndProviderUserId(
                        OAuthProvider.GITHUB,
                        "42"
                )
        ).hasValueSatisfying(result -> {
            assertThat(result.getId()).isEqualTo(savedIdentity.getId());
            assertThat(result.getMemberId()).isEqualTo(member.getId());
        });
    }

    @Test
    @DisplayName("같은 provider와 외부 ID를 중복 저장하면 커스텀 예외를 발생시킨다")
    void save_failure_duplicateProviderUser() {
        // given
        Member firstMember = memberRepository.save(
                Member.create(
                        "first",
                        null
                )
        );
        Member secondMember = memberRepository.save(
                Member.create(
                        "second",
                        null
                )
        );
        OAuthUser oauthUser = OAuthUser.of(
                OAuthProvider.GITHUB,
                "42",
                null
        );
        oauthIdentityRepository.save(
                OAuthIdentity.create(
                        oauthUser,
                        firstMember.getId()
                )
        );

        // when & then
        assertThatThrownBy(
                () -> oauthIdentityRepository.save(
                        OAuthIdentity.create(
                                oauthUser,
                                secondMember.getId()
                        )
                )
        ).isInstanceOfSatisfying(
                AuthException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(AuthErrorCode.NICKNAME_SETUP_ALREADY_COMPLETED)
        );
    }

    @Test
    @DisplayName("존재하지 않는 member 연결 오류는 중복 가입 오류로 변환하지 않는다")
    void save_failure_missingMember_propagatesConstraintViolation() {
        // given
        OAuthIdentity identity = OAuthIdentity.create(
                OAuthUser.of(
                        OAuthProvider.GITHUB,
                        "missing-member-user",
                        null
                ),
                999999L
        );

        // when & then
        assertThatThrownBy(() -> oauthIdentityRepository.save(identity))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
