package com.knot.backend.recording.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.knot.backend.global.exception.ErrorCode;
import com.knot.backend.global.exception.ProjectException;
import com.knot.backend.recording.application.dto.command.RecordingStartCommand;
import com.knot.backend.recording.application.dto.result.RecordingEndResult;
import com.knot.backend.recording.application.dto.result.RecordingStartResult;
import com.knot.backend.recording.domain.RecordingStatus;
import com.knot.backend.testsupport.TestApplicationProperties;
import com.knot.backend.testsupport.TestcontainersConfiguration;
import com.knot.backend.workspace.application.WorkspaceLeaveService;
import com.knot.backend.workspace.domain.WorkspaceErrorCode;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
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
import org.springframework.test.context.TestConstructor.AutowireMode;
import org.springframework.transaction.support.TransactionTemplate;

@Tag("integration")
@Import(TestcontainersConfiguration.class)
@TestApplicationProperties
@SpringBootTest
@TestConstructor(autowireMode = AutowireMode.ALL)
class RecordingEndServiceIntegrationTest {
    private static final Instant CREATED_AT = Instant.parse("2026-10-05T00:00:00Z");
    private static final Instant JOINED_AT = Instant.parse("2026-10-05T00:01:00Z");
    private static final String CONTROL_TOKEN = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefA";

    private final RecordingEndService recordingEndService;
    private final RecordingStartService recordingStartService;
    private final WorkspaceLeaveService workspaceLeaveService;
    private final TransactionTemplate transactionTemplate;
    private final JdbcClient jdbcClient;

    RecordingEndServiceIntegrationTest(
            RecordingEndService recordingEndService,
            RecordingStartService recordingStartService,
            WorkspaceLeaveService workspaceLeaveService,
            TransactionTemplate transactionTemplate,
            JdbcClient jdbcClient
    ) {
        this.recordingEndService = recordingEndService;
        this.recordingStartService = recordingStartService;
        this.workspaceLeaveService = workspaceLeaveService;
        this.transactionTemplate = transactionTemplate;
        this.jdbcClient = jdbcClient;
    }

    @BeforeEach
    void clearTables() {
        jdbcClient.sql("""
                TRUNCATE TABLE recording_sessions, workspace_invitations, workspace_members, workspaces,
                    oauth_identities, members
                RESTART IDENTITY CASCADE
                """)
                .update();
    }

    @Test
    @DisplayName("일시정지 중 종료하면 일시정지 구간을 누적하지 않고 종료 시각을 기록한다")
    void end_success_pausedRecordingKeepsAccumulatedTime() {
        // given
        long memberId = saveMember("member");
        long workspaceId = saveWorkspace("팀");
        saveWorkspaceMember(
                workspaceId,
                memberId,
                "OWNER"
        );
        long recordingId = savePausedRecording(
                workspaceId,
                memberId,
                30_000L
        );

        // when
        RecordingEndResult result = recordingEndService.end(
                workspaceId,
                memberId,
                recordingId
        );

        // then
        RecordingState state = recordingState(recordingId);
        assertThat(result.status()).isEqualTo(RecordingStatus.ENDED);
        assertThat(state.status()).isEqualTo("ENDED");
        assertThat(state.endedAt()).isEqualTo(result.endedAt());
        assertThat(state.accumulatedMillis()).isEqualTo(30_000L);
    }

    @Test
    @DisplayName("같은 녹음을 동시에 종료해도 하나의 종료 시각으로 수렴한다")
    void end_success_concurrentEndsConverge() throws Exception {
        // given
        long memberId = saveMember("member");
        long workspaceId = saveWorkspace("동시 종료 팀");
        saveWorkspaceMember(
                workspaceId,
                memberId,
                "OWNER"
        );
        long recordingId = startRecording(
                workspaceId,
                memberId
        );

        // when
        RaceResult<RecordingEndResult, RecordingEndResult> result = race(
                () -> recordingEndService.end(
                        workspaceId,
                        memberId,
                        recordingId
                ),
                () -> recordingEndService.end(
                        workspaceId,
                        memberId,
                        recordingId
                )
        );

        // then
        assertThat(
                result.first()
                        .errorCode()
        ).isNull();
        assertThat(
                result.second()
                        .errorCode()
        ).isNull();
        Instant endedAt = recordingState(recordingId).endedAt();
        assertThat(
                result.first()
                        .value()
                        .endedAt()
        ).isEqualTo(endedAt);
        assertThat(
                result.second()
                        .value()
                        .endedAt()
        ).isEqualTo(endedAt);
    }

    @Test
    @DisplayName("종료와 본인 탈퇴가 경합하면 종료 후 보존되거나 탈퇴 후 종료가 거절되고 폐기된다")
    void end_successOrRejected_whenRacingWithLeave() throws Exception {
        // given
        long ownerId = saveMember("owner");
        long memberId = saveMember("member");
        long workspaceId = saveWorkspace("종료 탈퇴 경합 팀");
        saveWorkspaceMember(
                workspaceId,
                ownerId,
                "OWNER"
        );
        saveWorkspaceMember(
                workspaceId,
                memberId,
                "MEMBER"
        );
        long recordingId = startRecording(
                workspaceId,
                memberId
        );

        // when
        RaceResult<RecordingEndResult, Void> result = race(
                () -> recordingEndService.end(
                        workspaceId,
                        memberId,
                        recordingId
                ),
                () -> {
                    workspaceLeaveService.leave(
                            memberId,
                            workspaceId
                    );
                    return null;
                }
        );

        // then
        assertThat(
                result.second()
                        .errorCode()
        ).isNull();
        String status = recordingState(recordingId).status();
        if (result.first()
                .errorCode() == null) {
            assertThat(status).isEqualTo("ENDED");
        } else {
            assertThat(
                    result.first()
                            .errorCode()
            ).isEqualTo(WorkspaceErrorCode.WORKSPACE_ACCESS_DENIED);
            assertThat(status).isEqualTo("DISCARDED");
        }
    }

    @Test
    @DisplayName("종료한 뒤에는 새 요청으로 다음 녹음을 시작할 수 있다")
    void end_success_allowsNextRecording() {
        // given
        long memberId = saveMember("member");
        long workspaceId = saveWorkspace("다음 녹음 팀");
        saveWorkspaceMember(
                workspaceId,
                memberId,
                "OWNER"
        );
        long recordingId = startRecording(
                workspaceId,
                memberId
        );
        recordingEndService.end(
                workspaceId,
                memberId,
                recordingId
        );

        // when
        RecordingStartResult next = recordingStartService.start(
                workspaceId,
                memberId,
                command()
        );

        // then
        assertThat(next.created()).isTrue();
        assertThat(next.recordingId()).isNotEqualTo(recordingId);
        assertThat(recordingState(recordingId).status()).isEqualTo("ENDED");
    }

    @Test
    @DisplayName("종료 후 같은 트랜잭션에서 예외가 나면 종료를 롤백한다")
    void end_failure_rollsBackWhenOuterTransactionFails() {
        // given
        long memberId = saveMember("member");
        long workspaceId = saveWorkspace("롤백 팀");
        saveWorkspaceMember(
                workspaceId,
                memberId,
                "OWNER"
        );
        long recordingId = startRecording(
                workspaceId,
                memberId
        );

        // when
        Throwable thrown = catchThrowable(() -> transactionTemplate.executeWithoutResult(status -> {
            recordingEndService.end(
                    workspaceId,
                    memberId,
                    recordingId
            );
            throw new IllegalStateException("종료 후 롤백 검증");
        }));

        // then
        assertThat(thrown).isInstanceOf(IllegalStateException.class);
        RecordingState state = recordingState(recordingId);
        assertThat(state.status()).isEqualTo("RECORDING");
        assertThat(state.endedAt()).isNull();
    }

    private <T, U> RaceResult<T, U> race(
            Callable<T> first,
            Callable<U> second
    ) throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executorService = Executors.newFixedThreadPool(2);
        try {
            Future<Outcome<T>> firstResult = executorService.submit(
                    outcomeAfterBarrier(
                            barrier,
                            first
                    )
            );
            Future<Outcome<U>> secondResult = executorService.submit(
                    outcomeAfterBarrier(
                            barrier,
                            second
                    )
            );
            return new RaceResult<>(
                    firstResult.get(
                            10,
                            TimeUnit.SECONDS
                    ),
                    secondResult.get(
                            10,
                            TimeUnit.SECONDS
                    )
            );
        } finally {
            executorService.shutdownNow();
        }
    }

    private <T> Callable<Outcome<T>> outcomeAfterBarrier(
            CyclicBarrier barrier,
            Callable<T> operation
    ) {
        return () -> {
            barrier.await(
                    5,
                    TimeUnit.SECONDS
            );
            try {
                return new Outcome<>(
                        operation.call(),
                        null
                );
            } catch (ProjectException exception) {
                return new Outcome<>(
                        null,
                        exception.getErrorCode()
                );
            }
        };
    }

    private long startRecording(
            long workspaceId,
            long memberId
    ) {
        return recordingStartService.start(
                workspaceId,
                memberId,
                command()
        )
                .recordingId();
    }

    private RecordingStartCommand command() {
        return new RecordingStartCommand(
                UUID.randomUUID(),
                UUID.randomUUID(),
                CONTROL_TOKEN
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
            long memberId,
            String role
    ) {
        jdbcClient.sql("""
                INSERT INTO workspace_members (workspace_id, member_id, role, joined_at)
                VALUES (:workspaceId, :memberId, :role, CAST(:joinedAt AS TIMESTAMPTZ))
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
                .param(
                        "joinedAt",
                        JOINED_AT.toString()
                )
                .update();
    }

    private long savePausedRecording(
            long workspaceId,
            long memberId,
            long accumulatedMillis
    ) {
        return jdbcClient.sql("""
                INSERT INTO recording_sessions (
                    workspace_id, member_id, request_id, tab_id, control_token_hash, status,
                    started_at, current_interval_started_at, ended_at, last_seen_at, accumulated_recording_millis
                )
                VALUES (
                    :workspaceId, :memberId, gen_random_uuid(), gen_random_uuid(), repeat('a', 64), 'PAUSED',
                    CAST(:startedAt AS TIMESTAMPTZ), NULL, NULL, CAST(:pausedAt AS TIMESTAMPTZ), :accumulatedMillis
                )
                RETURNING id
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
                        "startedAt",
                        JOINED_AT.toString()
                )
                .param(
                        "pausedAt",
                        JOINED_AT.plusMillis(accumulatedMillis)
                                .toString()
                )
                .param(
                        "accumulatedMillis",
                        accumulatedMillis
                )
                .query(Long.class)
                .single();
    }

    private RecordingState recordingState(long recordingId) {
        return jdbcClient.sql("""
                SELECT status, ended_at, accumulated_recording_millis
                FROM recording_sessions
                WHERE id = :recordingId
                """)
                .param(
                        "recordingId",
                        recordingId
                )
                .query(
                        (
                                resultSet,
                                rowNumber
                        ) -> {
                            OffsetDateTime endedAt = resultSet.getObject(
                                    "ended_at",
                                    OffsetDateTime.class
                            );
                            return new RecordingState(
                                    resultSet.getString("status"),
                                    endedAt == null ? null : endedAt.toInstant(),
                                    resultSet.getLong("accumulated_recording_millis")
                            );
                        }
                )
                .single();
    }

    private record RecordingState(
            String status,
            Instant endedAt,
            long accumulatedMillis
    ) {
    }

    private record Outcome<T>(
            T value,
            ErrorCode errorCode
    ) {
    }

    private record RaceResult<T, U>(
            Outcome<T> first,
            Outcome<U> second
    ) {
    }
}
