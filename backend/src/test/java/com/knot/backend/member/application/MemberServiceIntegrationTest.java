package com.knot.backend.member.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.knot.backend.member.domain.Member;
import com.knot.backend.testsupport.TestApplicationProperties;
import com.knot.backend.testsupport.TestcontainersConfiguration;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.TestConstructor.AutowireMode;

@Tag("integration")
@Import(TestcontainersConfiguration.class)
@TestApplicationProperties
@SpringBootTest
@TestConstructor(autowireMode = AutowireMode.ALL)
class MemberServiceIntegrationTest {
    private final MemberService memberService;
    private final JdbcTemplate jdbcTemplate;

    MemberServiceIntegrationTest(
            MemberService memberService,
            JdbcTemplate jdbcTemplate
    ) {
        this.memberService = memberService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @BeforeEach
    void clearMembers() {
        jdbcTemplate.update("""
                TRUNCATE TABLE notion_import_runs, notion_connections, notion_oauth_authorizations,
                    workspace_members, oauth_identities, members
                RESTART IDENTITY
                """);
    }

    @Test
    @DisplayName("닉네임으로 member를 생성하면 데이터베이스에 저장된다")
    void create_success() {
        // given

        // when
        Member result = memberService.create(
                "octocat",
                "https://example.com/avatar"
        );

        // then
        assertThat(result.getId()).isNotNull();
        assertThat(
                jdbcTemplate.queryForObject(
                        "SELECT nickname FROM members WHERE id = ?",
                        String.class,
                        result.getId()
                )
        ).isEqualTo("octocat");
    }

    @Test
    @DisplayName("저장된 member를 ID로 조회한다")
    void findById_success() {
        // given
        Member member = memberService.create(
                "octocat",
                null
        );

        // when
        Member result = memberService.findById(member.getId())
                .orElseThrow();

        // then
        assertThat(result.getNickname()).isEqualTo("octocat");
    }

    @Test
    @DisplayName("존재하지 않는 member ID는 빈 결과를 반환한다")
    void findById_success_missingMember() {
        // given

        // when
        Optional<Member> result = memberService.findById(1L);

        // then
        assertThat(result).isEmpty();
    }
}
