package com.knot.backend.recording.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.knot.backend.recording.domain.RecordingSession;
import com.knot.backend.recording.domain.RecordingSessionRepository;
import com.knot.backend.recording.domain.RecordingStatus;
import com.knot.backend.recording.domain.RecordingException;
import com.knot.backend.recording.domain.RecordingErrorCode;
import com.knot.backend.testsupport.TestcontainersConfiguration;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Tag("integration")
@Import({TestcontainersConfiguration.class, RecordingSessionRepositoryAdapter.class})
@DataJpaTest
class RecordingSessionRepositoryIntegrationTest {
    private static final String CONTROL_TOKEN_HASH = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    private static final Instant STARTED_AT = Instant.parse("2026-10-01T00:00:00Z");

    @Autowired
    private RecordingSessionRepository recordingSessionRepository;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private JdbcClient jdbcClient;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @DisplayName("녹음 세션을 저장하고 requestId로 조회한다")
    @Test
    void save_success() {
        // given
        long memberId = saveMember("recording-member");
        long workspaceId = saveWorkspaceWithMember(
                memberId,
                "녹음 팀"
        );
        UUID requestId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        RecordingSession recordingSession = startRecording(
                workspaceId,
                memberId,
                requestId
        );
        RecordingSession savedRecordingSession = saveAndFlush(recordingSession);
        entityManager.clear();

        // when
        RecordingSession foundRecordingSession = recordingSessionRepository.findByMemberIdAndRequestId(
                memberId,
                requestId
        )
                .orElseThrow();

        // then
        assertThat(foundRecordingSession.getId()).isEqualTo(savedRecordingSession.getId());
        assertThat(foundRecordingSession.getStatus()).isEqualTo(RecordingStatus.RECORDING);
        assertThat(foundRecordingSession.getStartedAt()).isEqualTo(STARTED_AT);
        assertThat(storedControlTokenHash(savedRecordingSession.getId())).isEqualTo(CONTROL_TOKEN_HASH);
    }

    @DisplayName("한 멤버는 활성 녹음을 하나만 가질 수 있다")
    @Test
    void save_failure_duplicateActiveMember() {
        // given
        long memberId = saveMember("active-member");
        long workspaceId = saveWorkspaceWithMember(
                memberId,
                "활성 팀"
        );
        saveAndFlush(
                startRecording(
                        workspaceId,
                        memberId,
                        UUID.fromString("11111111-1111-1111-1111-111111111111")
                )
        );
        RecordingSession secondRecordingSession = startRecording(
                workspaceId,
                memberId,
                UUID.fromString("22222222-2222-2222-2222-222222222222")
        );

        // when
        Throwable thrown = catchThrowable(() -> saveAndFlush(secondRecordingSession));

        // then
        assertThat(thrown).isInstanceOf(RecordingException.class)
                .extracting("errorCode")
                .isEqualTo(RecordingErrorCode.ACTIVE_RECORDING_ALREADY_EXISTS);
    }

    @DisplayName("종료된 녹음은 활성 녹음 제약에서 제외된다")
    @Test
    void save_success_afterEndedRecording() {
        // given
        long memberId = saveMember("ended-member");
        long workspaceId = saveWorkspaceWithMember(
                memberId,
                "종료 팀"
        );
        RecordingSession endedRecordingSession = startRecording(
                workspaceId,
                memberId,
                UUID.fromString("11111111-1111-1111-1111-111111111111")
        );
        endedRecordingSession.end(Instant.parse("2026-10-01T00:00:10Z"));
        saveAndFlush(endedRecordingSession);
        RecordingSession newRecordingSession = startRecording(
                workspaceId,
                memberId,
                UUID.fromString("22222222-2222-2222-2222-222222222222")
        );

        // when
        RecordingSession savedRecordingSession = saveAndFlush(newRecordingSession);

        // then
        assertThat(savedRecordingSession.getId()).isPositive();
        assertThat(recordingSessionRepository.existsActiveByMemberId(memberId)).isTrue();
    }

    @DisplayName("존재하지 않는 워크스페이스나 멤버의 녹음 세션은 저장할 수 없다")
    @Test
    void save_failure_foreignKey() {
        // given
        RecordingSession recordingSession = startRecording(
                Long.MAX_VALUE,
                Long.MAX_VALUE,
                UUID.fromString("11111111-1111-1111-1111-111111111111")
        );

        // when
        Throwable thrown = catchThrowable(() -> saveAndFlush(recordingSession));

        // then
        assertThat(thrown).isInstanceOf(RecordingException.class)
                .extracting("errorCode")
                .isEqualTo(RecordingErrorCode.INVALID_RECORDING_DATA);
    }

    @DisplayName("트랜잭션이 rollback되면 녹음 세션도 저장되지 않는다")
    @Test
    void save_success_rollback() {
        // given
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        // when
        transactionTemplate.executeWithoutResult(status -> {
            long memberId = saveMember("rollback-member");
            long workspaceId = saveWorkspaceWithMember(
                    memberId,
                    "롤백 팀"
            );
            RecordingSession recordingSession = startRecording(
                    workspaceId,
                    memberId,
                    UUID.fromString("11111111-1111-1111-1111-111111111111")
            );
            recordingSessionRepository.save(recordingSession);
            entityManager.flush();
            status.setRollbackOnly();
        });
        entityManager.clear();

        // then
        assertThat(countRecordingSessions()).isZero();
    }

    @Test
    @DisplayName("일시정지한 세션도 다른 워크스페이스의 활성 녹음 생성을 막는다")
    void save_failure_pausedSessionAcrossWorkspaces() {
        // given
        long memberId = saveMember("paused-member");
        long firstWorkspaceId = saveWorkspaceWithMember(
                memberId,
                "첫 팀"
        );
        long secondWorkspaceId = saveWorkspaceWithMember(
                memberId,
                "다음 팀"
        );
        RecordingSession first = startRecording(
                firstWorkspaceId,
                memberId,
                UUID.randomUUID()
        );
        first.pause(STARTED_AT.plusSeconds(10));
        saveAndFlush(first);
        RecordingSession second = startRecording(
                secondWorkspaceId,
                memberId,
                UUID.randomUUID()
        );

        // when
        Throwable failure = catchThrowable(() -> saveAndFlush(second));

        // then
        assertThat(failure).isInstanceOf(RecordingException.class)
                .extracting("errorCode")
                .isEqualTo(RecordingErrorCode.ACTIVE_RECORDING_ALREADY_EXISTS);
    }

    @Test
    @DisplayName("종료된 세션의 요청 키도 같은 멤버가 다른 세션에 재사용하지 못한다")
    void save_failure_reusesEndedRequestId() {
        // given
        long memberId = saveMember("key-member");
        long workspaceId = saveWorkspaceWithMember(
                memberId,
                "키 팀"
        );
        UUID requestId = UUID.randomUUID();
        RecordingSession first = startRecording(
                workspaceId,
                memberId,
                requestId
        );
        first.end(STARTED_AT.plusSeconds(10));
        saveAndFlush(first);
        RecordingSession second = startRecording(
                workspaceId,
                memberId,
                requestId
        );

        // when
        Throwable failure = catchThrowable(() -> saveAndFlush(second));

        // then
        assertThat(failure).isInstanceOf(RecordingException.class)
                .extracting("errorCode")
                .isEqualTo(RecordingErrorCode.RECORDING_START_REQUEST_CONFLICT);
    }

    private RecordingSession saveAndFlush(RecordingSession recordingSession) {
        RecordingSession savedRecordingSession = recordingSessionRepository.save(recordingSession);
        entityManager.flush();
        return savedRecordingSession;
    }

    private RecordingSession startRecording(
            long workspaceId,
            long memberId,
            UUID requestId
    ) {
        return RecordingSession.start(
                workspaceId,
                memberId,
                requestId,
                UUID.fromString("33333333-3333-3333-3333-333333333333"),
                CONTROL_TOKEN_HASH,
                STARTED_AT
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

    private long saveWorkspaceWithMember(
            long memberId,
            String name
    ) {
        long workspaceId = jdbcClient.sql("""
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
                        STARTED_AT.toString()
                )
                .query(Long.class)
                .single();
        jdbcClient.sql("""
                INSERT INTO workspace_members (workspace_id, member_id, role, joined_at, last_viewed)
                VALUES (:workspaceId, :memberId, 'OWNER', CAST(:joinedAt AS TIMESTAMPTZ), FALSE)
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
                        STARTED_AT.toString()
                )
                .update();
        return workspaceId;
    }

    private String storedControlTokenHash(long recordingSessionId) {
        return jdbcClient.sql("SELECT control_token_hash FROM recording_sessions WHERE id = :recordingSessionId")
                .param(
                        "recordingSessionId",
                        recordingSessionId
                )
                .query(String.class)
                .single();
    }

    private int countRecordingSessions() {
        return jdbcClient.sql("SELECT COUNT(*) FROM recording_sessions")
                .query(Integer.class)
                .single();
    }
}
