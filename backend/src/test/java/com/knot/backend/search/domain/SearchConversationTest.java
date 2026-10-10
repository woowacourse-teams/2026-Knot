package com.knot.backend.search.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SearchConversationTest {
    private static final Instant TIME = Instant.parse("2026-10-10T00:00:00Z");

    @Test
    @DisplayName("접수한 대화는 개인 범위와 시각을 갖고 목록에 표시된다")
    void create_success() {
        // when
        SearchConversation conversation = SearchConversation.create(
                1,
                2,
                TIME
        );

        // then
        assertThat(conversation.getWorkspaceId()).isEqualTo(1);
        assertThat(conversation.getMemberId()).isEqualTo(2);
        assertThat(conversation.getCreatedAt()).isEqualTo(TIME);
        assertThat(conversation.getUpdatedAt()).isEqualTo(TIME);
        assertThat(conversation.isVisibleInList()).isTrue();
    }

    @Test
    @DisplayName("최초 실패는 숨기고 retry STREAMING도 숨김을 유지하며 성공하면 복원한다")
    void recordFirstAnswerStatus_success() {
        // given
        SearchConversation conversation = SearchConversation.create(
                1,
                2,
                TIME
        );

        // when & then
        conversation.recordFirstAnswerStatus(SearchMessageStatus.FAILED);
        assertThat(conversation.isVisibleInList()).isFalse();
        conversation.recordFirstAnswerStatus(SearchMessageStatus.STREAMING);
        assertThat(conversation.isVisibleInList()).isFalse();
        conversation.recordFirstAnswerStatus(SearchMessageStatus.COMPLETED);
        assertThat(conversation.isVisibleInList()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1})
    @DisplayName("유효하지 않은 소유 범위로 대화를 만들 수 없다")
    void createInvalidScope_failure(long id) {
        // when & then
        assertThatExceptionOfType(SearchException.class).isThrownBy(
                () -> SearchConversation.create(
                        id,
                        2,
                        TIME
                )
        );
        assertThatExceptionOfType(SearchException.class).isThrownBy(
                () -> SearchConversation.create(
                        1,
                        id,
                        TIME
                )
        );
    }

    @Test
    @DisplayName("접수 시각은 필수다")
    void createMissingTime_failure() {
        // when & then
        assertThatExceptionOfType(SearchException.class).isThrownBy(
                () -> SearchConversation.create(
                        1,
                        2,
                        null
                )
        );
    }

    @Test
    @DisplayName("질문 상태와 null은 첫 답변 상태로 받을 수 없다")
    void recordFirstAnswerStatus_failure() {
        // given
        SearchConversation conversation = SearchConversation.create(
                1,
                2,
                TIME
        );

        // when & then
        assertThatExceptionOfType(SearchException.class)
                .isThrownBy(() -> conversation.recordFirstAnswerStatus(SearchMessageStatus.RECEIVED));
        assertThatExceptionOfType(SearchException.class).isThrownBy(() -> conversation.recordFirstAnswerStatus(null));
        assertThat(conversation.isVisibleInList()).isTrue();
    }
}
