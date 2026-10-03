package com.knot.backend.workspace.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.knot.backend.auth.domain.AuthenticatedMember;
import com.knot.backend.auth.domain.AuthTokenProvider;
import com.knot.backend.testsupport.TestApplicationProperties;
import com.knot.backend.testsupport.TestcontainersConfiguration;
import jakarta.servlet.http.Cookie;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Tag("acceptance")
@Import({TestcontainersConfiguration.class, WorkspaceInvitationQueryAcceptanceTest.ClockConfiguration.class})
@TestApplicationProperties
@SpringBootTest(properties = {"workspace.invitation.multiple-enabled=true", "knot.api-docs.enabled=true"})
@AutoConfigureMockMvc
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class WorkspaceInvitationQueryAcceptanceTest {
    private static final Instant NOW = Instant.now()
            .truncatedTo(ChronoUnit.MICROS);

    private final MockMvc mvc;
    private final JdbcClient jdbc;
    private final AuthTokenProvider tokens;
    private final ObjectMapper mapper;

    WorkspaceInvitationQueryAcceptanceTest(
            MockMvc mvc,
            JdbcClient jdbc,
            AuthTokenProvider tokens,
            ObjectMapper mapper
    ) {
        this.mvc = mvc;
        this.jdbc = jdbc;
        this.tokens = tokens;
        this.mapper = mapper;
    }

    @Test
    @DisplayName("OWNER는 유효 초대를 생성 시각·ID 역순으로 조회하고 비밀값과 무효 초대는 받지 않는다")
    void findAll_filtersAndSortsWithoutWriting() throws Exception {
        // given
        Fixture fixture = fixture("OWNER");
        long earlier = invitation(
                fixture.workspaceId(),
                NOW.minusSeconds(7200),
                false
        );
        long first = invitation(
                fixture.workspaceId(),
                NOW.minusSeconds(3600),
                false
        );
        long second = invitation(
                fixture.workspaceId(),
                NOW.minusSeconds(3600),
                false
        );
        invitation(
                fixture.workspaceId(),
                NOW.minusSeconds(1800),
                true
        );
        invitation(
                fixture.workspaceId(),
                NOW.minusSeconds(86400),
                false
        );
        invitation(
                fixture.workspaceId(),
                NOW.minusSeconds(86401),
                false
        );
        invitation(
                fixture.workspaceId(),
                NOW.plusSeconds(1),
                false
        );
        invitation(
                fixture("OWNER").workspaceId(),
                NOW,
                false
        );
        List<String> before = snapshot();

        // when
        ResultActions response = find(fixture);

        // then
        response.andExpect(status().isOk())
                .andExpect(
                        header().string(
                                "Cache-Control",
                                "no-store"
                        )
                );
        JsonNode body = body(response);
        assertThat(body.size()).isEqualTo(3);
        assertThat(
                List.of(
                        body.get(0)
                                .get("invitationId")
                                .asLong(),
                        body.get(1)
                                .get("invitationId")
                                .asLong(),
                        body.get(2)
                                .get("invitationId")
                                .asLong()
                )
        ).containsExactly(
                second,
                first,
                earlier
        );
        for (JsonNode item : body) {
            assertThat(item.propertyNames()).containsExactlyInAnyOrder(
                    "invitationId",
                    "createdAt",
                    "expiresAt"
            );
            assertThat(
                    Instant.parse(
                            item.get("expiresAt")
                                    .asText()
                    )
            ).isEqualTo(
                    Instant.parse(
                            item.get("createdAt")
                                    .asText()
                    )
                            .plusSeconds(86400)
            );
        }
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    @DisplayName("유효 초대가 없으면 OWNER에게 빈 배열을 반환한다")
    void findAll_returnsEmptyArray() throws Exception {
        // given
        Fixture fixture = fixture("OWNER");
        invitation(
                fixture.workspaceId(),
                NOW.minusSeconds(86400),
                false
        );
        invitation(
                fixture.workspaceId(),
                NOW.minusSeconds(100),
                true
        );

        // when
        ResultActions response = find(fixture);

        // then
        response.andExpect(status().isOk())
                .andExpect(
                        header().string(
                                "Cache-Control",
                                "no-store"
                        )
                );
        assertThat(body(response).isArray()).isTrue();
        assertThat(body(response).size()).isZero();
    }

    @ParameterizedTest
    @CsvSource({"MEMBER,403", "outsider,403", "left,403", "deleted,404", "missing,404", "invalid,400"})
    @DisplayName("OWNER가 아니거나 접근할 수 없는 Workspace의 초대 목록은 거절한다")
    void findAll_rejectsUnauthorizedAccess(
            String state,
            int expected
    ) throws Exception {
        // given
        Fixture fixture = fixture(state.equals("MEMBER") ? "MEMBER" : "OWNER");
        if (state.equals("outsider")) {
            fixture = new Fixture(
                    fixture.workspaceId(),
                    member()
            );
        } else if (state.equals("left")) {
            jdbc.sql("UPDATE workspace_members SET left_at = CURRENT_TIMESTAMP WHERE workspace_id = :id")
                    .param(
                            "id",
                            fixture.workspaceId()
                    )
                    .update();
        } else if (state.equals("deleted")) {
            jdbc.sql("UPDATE workspaces SET deleted_at = CURRENT_TIMESTAMP WHERE id = :id")
                    .param(
                            "id",
                            fixture.workspaceId()
                    )
                    .update();
        } else if (state.equals("missing")) {
            fixture = new Fixture(
                    Long.MAX_VALUE,
                    fixture.memberId()
            );
        } else if (state.equals("invalid")) {
            fixture = new Fixture(
                    0,
                    fixture.memberId()
            );
        }
        List<String> before = snapshot();

        // when
        ResultActions response = find(fixture);

        // then
        response.andExpect(status().is(expected))
                .andExpect(
                        header().string(
                                "Cache-Control",
                                "no-store"
                        )
                );
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    @DisplayName("승계 후에는 새 OWNER만 초대 목록을 조회한다")
    void findAll_usesCurrentOwner() throws Exception {
        // given
        Fixture previousOwner = fixture("OWNER");
        long successor = member();
        join(
                previousOwner.workspaceId(),
                successor,
                "MEMBER"
        );
        invitation(
                previousOwner.workspaceId(),
                NOW.minusSeconds(1),
                false
        );
        jdbc.sql("UPDATE workspace_members SET role = 'MEMBER' WHERE workspace_id = :id")
                .param(
                        "id",
                        previousOwner.workspaceId()
                )
                .update();
        jdbc.sql("UPDATE workspace_members SET role = 'OWNER' WHERE workspace_id = :id AND member_id = :memberId")
                .param(
                        "id",
                        previousOwner.workspaceId()
                )
                .param(
                        "memberId",
                        successor
                )
                .update();

        // when
        ResultActions response = find(
                new Fixture(
                        previousOwner.workspaceId(),
                        successor
                )
        );

        // then
        response.andExpect(status().isOk());
        assertThat(body(response).size()).isEqualTo(1);
        find(previousOwner).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("초대 목록은 인증을 요구하며 인증 오류에도 no-store를 적용한다")
    void findAll_requiresAuthentication() throws Exception {
        // given
        Fixture fixture = fixture("OWNER");

        // when
        ResultActions response = mvc.perform(
                get(
                        "/api/v1/workspaces/{id}/invitations",
                        fixture.workspaceId()
                )
        );

        // then
        response.andExpect(status().isUnauthorized())
                .andExpect(
                        header().string(
                                "Cache-Control",
                                "no-store"
                        )
                );
    }

    @Test
    @DisplayName("OpenAPI 초대 목록 스키마는 ID·생성·만료 시각만 공개한다")
    void openApi_hasNoSecretFields() throws Exception {
        // given
        String path = "/v3/api-docs";

        // when
        ResultActions response = mvc.perform(get(path));

        // then
        response.andExpect(status().isOk());
        JsonNode document = body(response);
        JsonNode properties = document.path("components")
                .path("schemas")
                .path("WorkspaceInvitationSummaryResponse")
                .path("properties");
        assertThat(properties.propertyNames()).containsExactlyInAnyOrder(
                "invitationId",
                "createdAt",
                "expiresAt"
        );
        JsonNode operation = document.path("paths")
                .path("/api/v1/workspaces/{workspaceId}/invitations")
                .path("get");
        assertThat(
                operation.path("responses")
                        .propertyNames()
        ).contains(
                "200",
                "400",
                "401",
                "403",
                "404"
        );
        assertThat(
                operation.path("security")
                        .isEmpty()
        ).isFalse();
    }

    private Fixture fixture(String role) {
        long memberId = member();
        long workspaceId = jdbc
                .sql("INSERT INTO workspaces (name, created_at) VALUES ('초대 목록 팀', CURRENT_TIMESTAMP) RETURNING id")
                .query(Long.class)
                .single();
        join(
                workspaceId,
                memberId,
                role
        );
        return new Fixture(
                workspaceId,
                memberId
        );
    }

    private long member() {
        return jdbc.sql("INSERT INTO members (nickname) VALUES ('목록 멤버') RETURNING id")
                .query(Long.class)
                .single();
    }

    private void join(
            long workspaceId,
            long memberId,
            String role
    ) {
        jdbc.sql("""
                INSERT INTO workspace_members (workspace_id, member_id, role, joined_at)
                VALUES (:workspaceId, :memberId, :role, CURRENT_TIMESTAMP)
                """)
                .param(
                        "workspaceId",
                        workspaceId
                )
                .param(
                        "memberId",
                        memberId
                )
                .param(
                        "role",
                        role
                )
                .update();
    }

    private long invitation(
            long workspaceId,
            Instant createdAt,
            boolean invalidated
    ) {
        return jdbc.sql("""
                INSERT INTO workspace_invitations (workspace_id, link_token_hash, invite_code_hash,
                    created_at, expires_at, invalidated_at)
                VALUES (:workspaceId, :hash, :hash, :createdAt, :expiresAt, :invalidatedAt) RETURNING id
                """)
                .param(
                        "workspaceId",
                        workspaceId
                )
                .param(
                        "hash",
                        UUID.randomUUID()
                                .toString()
                )
                .param(
                        "createdAt",
                        createdAt.atOffset(ZoneOffset.UTC)
                )
                .param(
                        "expiresAt",
                        createdAt.plusSeconds(86400)
                                .atOffset(ZoneOffset.UTC)
                )
                .param(
                        "invalidatedAt",
                        invalidated ? createdAt.atOffset(ZoneOffset.UTC) : null
                )
                .query(Long.class)
                .single();
    }

    private ResultActions find(Fixture fixture) throws Exception {
        Cookie cookie = new Cookie(
                "KNOT_ACCESS_TOKEN",
                tokens.issue(
                        AuthenticatedMember.of(
                                fixture.memberId(),
                                "목록 멤버",
                                null
                        )
                )
        );
        return mvc.perform(
                get(
                        "/api/v1/workspaces/{id}/invitations",
                        fixture.workspaceId()
                ).cookie(cookie)
        );
    }

    private JsonNode body(ResultActions response) throws Exception {
        return mapper.readTree(
                response.andReturn()
                        .getResponse()
                        .getContentAsString()
        );
    }

    private List<String> snapshot() {
        return jdbc.sql("""
                SELECT 'invitation:' || row_to_json(i)::text AS snapshot FROM workspace_invitations i
                UNION ALL SELECT 'membership:' || row_to_json(m)::text FROM workspace_members m
                ORDER BY snapshot
                """)
                .query(String.class)
                .list();
    }

    private record Fixture(
            long workspaceId,
            long memberId
    ) {
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ClockConfiguration {
        @Bean
        @Primary
        Clock invitationListClock() {
            return Clock.fixed(
                    NOW,
                    ZoneOffset.UTC
            );
        }
    }
}
