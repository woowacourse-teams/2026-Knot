package com.knot.backend.document.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

class DocumentConfirmationCursorTest {
    @ParameterizedTest
    @EnumSource(DocumentConfirmationState.class)
    @DisplayName("상태와 마지막 대상 ID를 요청 범위와 함께 커서로 만든다")
    void of_success_allStates(DocumentConfirmationState state) {
        // when
        DocumentConfirmationCursor cursor = DocumentConfirmationCursor.of(
                1,
                3,
                2,
                state,
                7
        );
        // then
        assertThat(cursor.getWorkspaceId()).isEqualTo(1);
        assertThat(cursor.getDocumentId()).isEqualTo(3);
        assertThat(cursor.getMemberId()).isEqualTo(2);
        assertThat(cursor.getState()).isEqualTo(state);
        assertThat(cursor.getTargetMemberId()).isEqualTo(7);
    }

    @ParameterizedTest
    @CsvSource({"0,3,2,7", "1,0,2,7", "1,3,-1,7", "1,3,2,0"})
    @DisplayName("양수가 아닌 범위 또는 대상 ID는 거절한다")
    void of_failure_invalidIdentifiers(
            long workspaceId,
            long documentId,
            long memberId,
            long targetId
    ) {
        // when & then
        assertThatThrownBy(
                () -> DocumentConfirmationCursor.of(
                        workspaceId,
                        documentId,
                        memberId,
                        DocumentConfirmationState.PENDING,
                        targetId
                )
        ).isInstanceOf(DocumentException.class);
    }

    @Test
    @DisplayName("상태가 없는 커서는 생성하지 않는다")
    void of_failure_missingState() {
        // when & then
        assertThatThrownBy(
                () -> DocumentConfirmationCursor.of(
                        1,
                        3,
                        2,
                        null,
                        7
                )
        ).isInstanceOf(DocumentException.class);
    }

    private String encode(String payload) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    }
}
