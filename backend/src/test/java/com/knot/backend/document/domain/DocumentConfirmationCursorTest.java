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
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

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

    @Test
    @DisplayName("버전·문서 범위·상태·대상을 padding 없는 Base64URL로 인코딩한다")
    void encode_success_payload() {
        // given
        DocumentConfirmationCursor cursor = DocumentConfirmationCursor.of(
                1,
                3,
                2,
                DocumentConfirmationState.PENDING,
                7
        );
        // when
        String encoded = cursor.encode();
        // then
        assertThat(encoded).isEqualTo(encode("1|1|3|2|PENDING|7"))
                .doesNotContain("=");
    }

    @ParameterizedTest
    @EnumSource(DocumentConfirmationState.class)
    @DisplayName("같은 요청 범위에서 커서의 상태와 대상 경계를 복원한다")
    void parse_success_roundTrip(DocumentConfirmationState state) {
        // given
        String encoded = DocumentConfirmationCursor.of(
                1,
                3,
                2,
                state,
                7
        )
                .encode();
        // when
        DocumentConfirmationCursor cursor = DocumentConfirmationCursor.parse(
                encoded,
                1,
                3,
                2
        );
        // then
        assertThat(cursor.getState()).isEqualTo(state);
        assertThat(cursor.getTargetMemberId()).isEqualTo(7);
    }

    @ParameterizedTest
    @CsvSource({"9,3,2", "1,9,2", "1,3,9"})
    @DisplayName("다른 Workspace·문서·조회 멤버의 커서는 거절한다")
    void parse_failure_scopeMismatch(
            long workspaceId,
            long documentId,
            long memberId
    ) {
        // given
        String encoded = DocumentConfirmationCursor.of(
                1,
                3,
                2,
                DocumentConfirmationState.CONFIRMED,
                7
        )
                .encode();
        // when & then
        assertThatThrownBy(
                () -> DocumentConfirmationCursor.parse(
                        encoded,
                        workspaceId,
                        documentId,
                        memberId
                )
        ).isInstanceOf(DocumentException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "%notBase64"})
    @DisplayName("비어 있거나 잘못 인코딩된 커서를 거절한다")
    void parse_failure_invalidEncoding(String encoded) {
        // when & then
        assertThatThrownBy(
                () -> DocumentConfirmationCursor.parse(
                        encoded,
                        1,
                        3,
                        2
                )
        ).isInstanceOf(DocumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"2|1|3|2|PENDING|7", "1|1|3|2|PENDING", "1|1|3|2|PENDING|7|extra", "1|1|3|2|NOT_REQUIRED|7",
            "1|1|3|2|PENDING|0", "1|one|3|2|PENDING|7", "1|1|3|2|PENDING|9223372036854775808"})
    @DisplayName("버전·필드 수·상태·숫자가 잘못된 커서는 INVALID_PARAMETER다")
    void parse_failure_invalidPayload(String payload) {
        // when & then
        assertThatThrownBy(
                () -> DocumentConfirmationCursor.parse(
                        encode(payload),
                        1,
                        3,
                        2
                )
        ).isInstanceOf(DocumentException.class)
                .extracting(
                        error -> ((DocumentException) error).getErrorCode()
                                .getCode()
                )
                .isEqualTo("INVALID_PARAMETER");
    }

    @Test
    @DisplayName("허용 길이를 초과한 커서는 거절한다")
    void parse_failure_tooLong() {
        // when & then
        assertThatThrownBy(
                () -> DocumentConfirmationCursor.parse(
                        "A".repeat(513),
                        1,
                        3,
                        2
                )
        ).isInstanceOf(DocumentException.class);
    }

    private String encode(String payload) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    }
}
