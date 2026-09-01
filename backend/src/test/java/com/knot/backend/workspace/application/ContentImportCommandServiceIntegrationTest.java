package com.knot.backend.workspace.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.knot.backend.testsupport.TestApplicationProperties;
import com.knot.backend.testsupport.TestcontainersConfiguration;
import com.knot.backend.workspace.application.dto.result.ContentImportRunRequestResult;
import com.knot.backend.workspace.domain.ContentSourceProvider;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestConstructor;

@Tag("integration")
@Import(TestcontainersConfiguration.class)
@TestApplicationProperties
@SpringBootTest
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class ContentImportCommandServiceIntegrationTest {
    private static final Instant CREATED_AT = Instant.parse("2026-09-01T00:00:00Z");
    private static final ContentSourceProvider PROVIDER = ContentSourceProvider.values()[0];

    private final ContentImportCommandService service;
    private final JdbcClient jdbcClient;

    ContentImportCommandServiceIntegrationTest(
            ContentImportCommandService service,
            JdbcClient jdbcClient
    ) {
        this.service = service;
        this.jdbcClient = jdbcClient;
    }

    @BeforeEach
    void clearTables() {
        jdbcClient.sql("""
                TRUNCATE TABLE imported_pages, content_import_runs, content_source_connections,
                    content_source_authorizations, workspace_members, workspaces, oauth_identities, members
                RESTART IDENTITY CASCADE
                """)
                .update();
    }

    @DisplayName("OWNER의 시작 요청은 PostgreSQL에 하나의 PENDING Run을 저장한다")
    @Test
    void start_success_persistsPendingRun() {
        // given
        TestContext context = saveContext("owner");

        // when
        ContentImportRunRequestResult result = service.start(
                context.workspaceId(),
                context.memberId(),
                PROVIDER
        );

        // then
        assertThat(result.created()).isTrue();
        assertThat(importRunSnapshot(result.id())).isEqualTo(
                "%d|%d|%d|PENDING|null|0|null|null".formatted(
                        context.workspaceId(),
                        context.connectionId(),
                        context.memberId()
                )
        );
    }

    @DisplayName("같은 Connection의 동시 시작 요청은 하나의 활성 Run ID로 수렴한다")
    @Test
    void start_success_concurrentRequestsConverge() throws Exception {
        // given
        TestContext context = saveContext("owner");

        // when
        List<ContentImportRunRequestResult> results = startConcurrently(context);

        // then
        assertThat(results).extracting(ContentImportRunRequestResult::created)
                .containsExactlyInAnyOrder(
                        true,
                        false
                );
        assertThat(results).extracting(ContentImportRunRequestResult::id)
                .containsOnly(
                        results.getFirst()
                                .id()
                );
        assertThat(activeImportRunCount(context.connectionId())).isOne();
        assertThat(importRunCount()).isOne();
    }

    private List<ContentImportRunRequestResult> startConcurrently(TestContext context) throws Exception {
        ExecutorService executorService = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<ContentImportRunRequestResult>> futures = new ArrayList<>();
            for (int index = 0; index < 2; index++) {
                futures.add(executorService.submit(() -> {
                    ready.countDown();
                    if (!start.await(
                            10,
                            TimeUnit.SECONDS
                    )) {
                        throw new IllegalStateException("동시 시작 준비가 완료되지 않았습니다");
                    }
                    return service.start(
                            context.workspaceId(),
                            context.memberId(),
                            PROVIDER
                    );
                }));
            }
            if (!ready.await(
                    10,
                    TimeUnit.SECONDS
            )) {
                throw new IllegalStateException("동시 시작 작업이 준비되지 않았습니다");
            }
            start.countDown();
            List<ContentImportRunRequestResult> results = new ArrayList<>();
            for (Future<ContentImportRunRequestResult> future : futures) {
                results.add(
                        future.get(
                                10,
                                TimeUnit.SECONDS
                        )
                );
            }
            return results;
        } finally {
            executorService.shutdownNow();
        }
    }

    private TestContext saveContext(String nickname) {
        long memberId = saveMember(nickname);
        long workspaceId = saveWorkspace(nickname + " 팀");
        saveWorkspaceMember(
                workspaceId,
                memberId
        );
        long connectionId = saveConnection(
                workspaceId,
                memberId
        );
        return new TestContext(
                memberId,
                workspaceId,
                connectionId
        );
    }

    private long saveMember(String nickname) {
        return jdbcClient.sql("""
                INSERT INTO members (nickname, profile_image_url)
                VALUES (:nickname, NULL)
                RETURNING id
                """)
                .param(
                        "nickname",
                        nickname
                )
                .query(Long.class)
                .single();
    }

    private long saveWorkspace(String name) {
        return jdbcClient.sql("""
                INSERT INTO workspaces (name, created_at)
                VALUES (:name, CAST(:createdAt AS TIMESTAMPTZ))
                RETURNING id
                """)
                .param(
                        "name",
                        name
                )
                .param(
                        "createdAt",
                        CREATED_AT.toString()
                )
                .query(Long.class)
                .single();
    }

    private void saveWorkspaceMember(
            long workspaceId,
            long memberId
    ) {
        jdbcClient.sql("""
                INSERT INTO workspace_members (workspace_id, member_id, role, joined_at)
                VALUES (:workspaceId, :memberId, 'OWNER', CAST(:joinedAt AS TIMESTAMPTZ))
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
                        "joinedAt",
                        CREATED_AT.toString()
                )
                .update();
    }

    private long saveConnection(
            long workspaceId,
            long authorizingMemberId
    ) {
        return jdbcClient.sql("""
                INSERT INTO content_source_connections (
                    workspace_id,
                    provider,
                    access_credential_ciphertext,
                    external_source_id,
                    provider_connection_id,
                    authorization_owner_type,
                    authorizing_member_id,
                    created_at,
                    updated_at,
                    version
                ) VALUES (
                    :workspaceId,
                    :provider,
                    'encrypted-access-token',
                    :externalSourceId,
                    :providerConnectionId,
                    'WORKSPACE',
                    :authorizingMemberId,
                    CAST(:createdAt AS TIMESTAMPTZ),
                    CAST(:createdAt AS TIMESTAMPTZ),
                    0
                )
                RETURNING id
                """)
                .param(
                        "workspaceId",
                        workspaceId
                )
                .param(
                        "provider",
                        PROVIDER.name()
                )
                .param(
                        "externalSourceId",
                        "external-source-" + workspaceId
                )
                .param(
                        "providerConnectionId",
                        "provider-connection-" + workspaceId
                )
                .param(
                        "authorizingMemberId",
                        authorizingMemberId
                )
                .param(
                        "createdAt",
                        CREATED_AT.toString()
                )
                .query(Long.class)
                .single();
    }

    private String importRunSnapshot(long importRunId) {
        return jdbcClient.sql("""
                SELECT CONCAT_WS(
                    '|',
                    workspace_id,
                    content_source_connection_id,
                    requested_by_member_id,
                    status,
                    COALESCE(total_page_count::text, 'null'),
                    processed_page_count,
                    COALESCE(started_at::text, 'null'),
                    COALESCE(completed_at::text, 'null')
                )
                FROM content_import_runs
                WHERE id = :importRunId
                """)
                .param(
                        "importRunId",
                        importRunId
                )
                .query(String.class)
                .single();
    }

    private long activeImportRunCount(long connectionId) {
        return jdbcClient.sql("""
                SELECT COUNT(*)
                FROM content_import_runs
                WHERE content_source_connection_id = :connectionId
                    AND status IN ('PENDING', 'RUNNING')
                """)
                .param(
                        "connectionId",
                        connectionId
                )
                .query(Long.class)
                .single();
    }

    private long importRunCount() {
        return jdbcClient.sql("SELECT COUNT(*) FROM content_import_runs")
                .query(Long.class)
                .single();
    }

    private record TestContext(
            long memberId,
            long workspaceId,
            long connectionId
    ) {
    }
}
