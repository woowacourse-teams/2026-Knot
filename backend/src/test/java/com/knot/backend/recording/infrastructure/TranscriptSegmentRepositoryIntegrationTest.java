package com.knot.backend.recording.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.knot.backend.document.DocumentFixtures;
import com.knot.backend.recording.domain.TranscriptSegment;
import com.knot.backend.recording.domain.TranscriptSegmentRepository;
import com.knot.backend.testsupport.TestcontainersConfiguration;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;

@Tag("integration")
@DataJpaTest
@Import({TestcontainersConfiguration.class, TranscriptSegmentRepositoryAdapter.class})
class TranscriptSegmentRepositoryIntegrationTest {

    @Autowired
    private TranscriptSegmentRepository segments;
    @Autowired
    private JdbcClient jdbc;
    @Autowired
    private EntityManager entityManager;
    private DocumentFixtures fixtures;
    private long recordingId;
    private long transcriptId;

    @BeforeEach
    void setUp() {
        fixtures = new DocumentFixtures(jdbc);
        long workspaceId = fixtures.saveWorkspace();
        long memberId = fixtures.saveMember("녹음자");
        recordingId = fixtures.saveRecording(
                workspaceId,
                memberId,
                10000
        );
        transcriptId = fixtures.saveTranscript(recordingId);
    }

    @Test
    @DisplayName("저장한 구간을 원문별 시작 시각·동률 순서로 모두 읽는다")
    void findAllByTranscriptId_success_orderAndScope() {
        // given
        long otherTranscript = fixtures.saveTranscript(recordingId);
        segments.saveAll(
                List.of(
                        TranscriptSegment.create(
                                transcriptId,
                                2,
                                1200,
                                1600L,
                                2,
                                "세 번째"
                        ),
                        TranscriptSegment.create(
                                transcriptId,
                                1,
                                1200,
                                null,
                                null,
                                "두 번째"
                        ),
                        TranscriptSegment.create(
                                transcriptId,
                                0,
                                0,
                                100L,
                                1,
                                "첫 번째"
                        ),
                        TranscriptSegment.create(
                                otherTranscript,
                                0,
                                0,
                                null,
                                null,
                                "다른 원문"
                        )
                )
        );
        entityManager.flush();
        entityManager.clear();

        // when
        List<TranscriptSegment> result = segments.findAllByTranscriptId(transcriptId);

        // then
        assertThat(result).extracting(TranscriptSegment::getText)
                .containsExactly(
                        "첫 번째",
                        "두 번째",
                        "세 번째"
                );
        assertThat(
                result.get(1)
                        .getEndMillis()
        ).isNull();
        assertThat(
                result.get(1)
                        .getSpeakerNumber()
        ).isNull();
        assertThat(
                result.get(2)
                        .getEndMillis()
        ).isEqualTo(1600);
        assertThat(
                result.get(2)
                        .getSpeakerNumber()
        ).isEqualTo(2);
        assertThat(result).extracting(TranscriptSegment::getId)
                .doesNotContainNull();
    }

    @Test
    @DisplayName("없는 원문의 구간 조회는 빈 목록이다")
    void findAllByTranscriptId_success_absent() {
        // when & then
        assertThat(segments.findAllByTranscriptId(Long.MAX_VALUE)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {":id, -1, 0, NULL, NULL, '발언'", ":id, 0, -1, NULL, NULL, '발언'",
            ":id, 0, 100, 99, NULL, '발언'", ":id, 0, 0, NULL, 0, '발언'", ":id, 0, 0, NULL, -1, '발언'",
            ":id, 0, NULL, NULL, NULL, '발언'", ":id, NULL, 0, NULL, NULL, '발언'", "NULL, 0, 0, NULL, NULL, '발언'",
            ":id, 0, 0, NULL, NULL, NULL", ":id, 0, 0, NULL, NULL, ''", ":id, 0, 0, NULL, NULL, E' \\t\\n'",
            ":id, 0, 0, NULL, NULL, U&'\\2003'"})
    @DisplayName("Entity를 우회한 잘못된 구간도 DB가 거절한다")
    void saveAll_failure_databaseConstraints(String values) {
        // when & then
        assertThatThrownBy(
                () -> jdbc.sql(
                        "INSERT INTO transcript_segments "
                                + "(transcript_id, position, start_millis, end_millis, speaker_number, text) VALUES ("
                                + values + ")"
                )
                        .param(
                                "id",
                                transcriptId
                        )
                        .update()
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("같은 원문의 중복 저장 순서를 DB가 거절한다")
    void saveAll_failure_duplicatePosition() {
        // given
        segments.saveAll(
                List.of(
                        TranscriptSegment.create(
                                transcriptId,
                                0,
                                0,
                                null,
                                null,
                                "발언"
                        )
                )
        );
        entityManager.flush();

        // when & then
        assertThatThrownBy(
                () -> jdbc
                        .sql(
                                "INSERT INTO transcript_segments (transcript_id, position, start_millis, text) "
                                        + "VALUES (:id, 0, 100, '중복')"
                        )
                        .param(
                                "id",
                                transcriptId
                        )
                        .update()
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("없는 원문을 참조하는 구간을 DB가 거절한다")
    void saveAll_failure_missingTranscript() {
        // when & then
        assertThatThrownBy(
                () -> segments.saveAll(
                        List.of(
                                TranscriptSegment.create(
                                        Long.MAX_VALUE,
                                        0,
                                        0,
                                        null,
                                        null,
                                        "발언"
                                )
                        )
                )
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("구간이 남아 있는 원문의 삭제를 RESTRICT로 막는다")
    void deleteTranscript_failure_restricted() {
        // given
        segments.saveAll(
                List.of(
                        TranscriptSegment.create(
                                transcriptId,
                                0,
                                0,
                                null,
                                null,
                                "발언"
                        )
                )
        );
        entityManager.flush();

        // when & then
        assertThatThrownBy(
                () -> jdbc.sql("DELETE FROM transcripts WHERE id = :id")
                        .param(
                                "id",
                                transcriptId
                        )
                        .update()
        ).isInstanceOf(DataIntegrityViolationException.class);
    }
}
