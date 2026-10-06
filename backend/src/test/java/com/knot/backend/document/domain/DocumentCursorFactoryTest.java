package com.knot.backend.document.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DocumentCursorFactoryTest {
    private static final Instant TIME = Instant.parse("2026-10-06T00:00:00.123456Z");

    @Test
    @DisplayName("커서 생성은 조회 범위와 정밀한 페이지 경계를 보존한다")
    void of_success_scopeAndBoundary() {
        DocumentCursor cursor = DocumentCursor.of(
                1,
                2,
                MyConfirmationState.PENDING,
                42L,
                TIME,
                301
        );

        assertThat(cursor.getWorkspaceId()).isEqualTo(1);
        assertThat(cursor.getMemberId()).isEqualTo(2);
        assertThat(cursor.getMyConfirmation()).isEqualTo(MyConfirmationState.PENDING);
        assertThat(cursor.getRecordingSessionId()).isEqualTo(42);
        assertThat(cursor.getCreatedAt()).isEqualTo(TIME);
        assertThat(cursor.getDocumentId()).isEqualTo(301);
    }

    @Test
    @DisplayName("문서 경계 ID가 양수가 아니거나 시각이 없으면 커서를 생성하지 않는다")
    void of_failure_invalidBoundary() {
        assertThatThrownBy(
                () -> DocumentCursor.of(
                        1,
                        2,
                        null,
                        null,
                        TIME,
                        0
                )
        ).isInstanceOf(DocumentException.class);
        assertThatThrownBy(
                () -> DocumentCursor.of(
                        1,
                        2,
                        null,
                        null,
                        null,
                        301
                )
        ).isInstanceOf(DocumentException.class);
    }
}
