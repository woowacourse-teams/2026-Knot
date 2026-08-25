package com.knot.backend.member.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import org.springframework.transaction.annotation.Transactional;

@Tag("integration")
@Import(TestcontainersConfiguration.class)
@TestApplicationProperties
@SpringBootTest
@Transactional
@TestConstructor(autowireMode = AutowireMode.ALL)
class MemberRepositoryTest {
    private final JdbcTemplate jdbcTemplate;
    private final MemberRepository memberRepository;
    private final MemberJpaRepository memberJpaRepository;

    MemberRepositoryTest(
            JdbcTemplate jdbcTemplate,
            MemberRepository memberRepository,
            MemberJpaRepository memberJpaRepository
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.memberRepository = memberRepository;
        this.memberJpaRepository = memberJpaRepository;
    }

    @BeforeEach
    void clearMembers() {
        memberJpaRepository.deleteAll();
    }

    @Test
    @DisplayName("로그인 프로필을 저장하고 GitHub ID로 조회한다")
    void insertIfAbsent_success() {
        // given
        Member member = Member.create(
                42L,
                "octocat",
                "https://example.com/avatar"
        );

        // when
        memberRepository.insertIfAbsent(member);

        // then
        assertThat(memberRepository.findByGithubId(42L)).get()
                .satisfies(savedMember -> {
                    assertThat(savedMember.getGithubId()).isEqualTo(42L);
                    assertThat(savedMember.getNickname()).isEqualTo("octocat");
                    assertThat(savedMember.getProfileImageUrl())
                            .isEqualTo("https://example.com/avatar");
                });
    }

    @Test
    @DisplayName("같은 GitHub ID의 로그인 프로필은 기존 정보를 덮어쓰지 않는다")
    void insertIfAbsent_doesNotOverwriteExistingMember() {
        // given
        memberRepository.insertIfAbsent(
                Member.create(
                        42L,
                        "old-name",
                        "https://example.com/old"
                )
        );

        // when
        memberRepository.insertIfAbsent(
                Member.create(
                        42L,
                        "new-name",
                        "https://example.com/new"
                )
        );

        // then
        assertThat(memberRepository.findByGithubId(42L)).get()
                .extracting("nickname")
                .isEqualTo("old-name");
        assertThat(memberRepository.findByGithubId(42L)).get()
                .extracting("profileImageUrl")
                .isEqualTo("https://example.com/old");
    }

    @Test
    @DisplayName("GitHub ID가 양수가 아니면 데이터베이스가 저장을 거부한다")
    void save_failure_nonPositiveGithubId() {
        // when & then
        assertThatThrownBy(
                () -> jdbcTemplate.update(
                        "INSERT INTO members (github_id, nickname) VALUES (?, ?)",
                        0L,
                        "octocat"
                )
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("공백 nickname이면 데이터베이스가 저장을 거부한다")
    void save_failure_blankNickname() {
        // when & then
        assertThatThrownBy(
                () -> jdbcTemplate.update(
                        "INSERT INTO members (github_id, nickname) VALUES (?, ?)",
                        43L,
                        "   "
                )
        ).isInstanceOf(DataIntegrityViolationException.class);
    }
}
