package com.knot.backend.member.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.knot.backend.member.domain.Member;
import com.knot.backend.member.domain.MemberRepository;
import com.knot.backend.testsupport.TestApplicationProperties;
import com.knot.backend.testsupport.TestcontainersConfiguration;
import java.util.Optional;
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
class MemberRepositoryTest {
    private final JdbcTemplate jdbcTemplate;
    private final MemberRepository memberRepository;

    MemberRepositoryTest(
            JdbcTemplate jdbcTemplate,
            MemberRepository memberRepository
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.memberRepository = memberRepository;
    }

    @BeforeEach
    void clearMembers() {
        jdbcTemplate.update("TRUNCATE TABLE oauth_identities, members RESTART IDENTITY");
    }

    @Test
    @DisplayName("member를 저장하고 ID로 조회한다")
    void save_success() {
        // given
        Member member = Member.create(
                "octocat",
                "https://example.com/avatar"
        );

        // when
        Member savedMember = memberRepository.save(member);

        // then
        assertThat(memberRepository.findById(savedMember.getId())).hasValueSatisfying(result -> {
            assertThat(result.getNickname()).isEqualTo("octocat");
            assertThat(result.getProfileImageUrl()).isEqualTo("https://example.com/avatar");
        });
    }

    @Test
    @DisplayName("존재하지 않는 member ID를 조회하면 빈 결과를 반환한다")
    void findById_missingMember_success() {
        // when
        Optional<Member> result = memberRepository.findById(1L);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("공백 nickname이면 데이터베이스가 저장을 거부한다")
    void save_failure_blankNickname() {
        // when & then
        assertThatThrownBy(
                () -> jdbcTemplate.update(
                        "INSERT INTO members (nickname) VALUES (?)",
                        "   "
                )
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("공백 프로필 이미지 URL이면 데이터베이스가 저장을 거부한다")
    void save_failure_blankProfileImageUrl() {
        // when & then
        assertThatThrownBy(
                () -> jdbcTemplate.update(
                        "INSERT INTO members (nickname, profile_image_url) VALUES (?, ?)",
                        "octocat",
                        "   "
                )
        ).isInstanceOf(DataIntegrityViolationException.class);
    }
}
