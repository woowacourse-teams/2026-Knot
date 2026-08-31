package com.knot.backend.chat.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ChatStreamHandleTest {

    @Test
    @DisplayName("스트림 취소는 취소 작업을 한 번만 실행한다")
    void cancel_success_once() {
        // given
        AtomicInteger cancelCount = new AtomicInteger();
        ChatStreamHandle handle = new ChatStreamHandle(cancelCount::incrementAndGet);

        // when
        boolean firstCancel = handle.cancel();
        boolean secondCancel = handle.cancel();

        // then
        assertThat(firstCancel).isTrue();
        assertThat(secondCancel).isFalse();
        assertThat(cancelCount).hasValue(1);
        assertThat(handle.isCancelled()).isTrue();
    }

    @Test
    @DisplayName("완료가 시작된 스트림은 취소하지 않는다")
    void cancel_failure_afterCompletionStarted() {
        // given
        AtomicInteger cancelCount = new AtomicInteger();
        ChatStreamHandle handle = new ChatStreamHandle(cancelCount::incrementAndGet);

        // when
        boolean completionStarted = handle.beginCompletion();
        boolean cancelled = handle.cancel();

        // then
        assertThat(completionStarted).isTrue();
        assertThat(cancelled).isFalse();
        assertThat(cancelCount).hasValue(0);
        assertThat(handle.isCancelled()).isFalse();
    }
}
