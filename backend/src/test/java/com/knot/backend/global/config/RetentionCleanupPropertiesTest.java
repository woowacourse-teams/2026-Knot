package com.knot.backend.global.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.knot.backend.recording.domain.RecordingException;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RetentionCleanupPropertiesTest {

    @Test
    @DisplayName("보존 정리는 기본 비활성이며 삭제 lease와 재확인 대기 값이 유효하다")
    void validate_successDefaultsDisabled() {
        RetentionCleanupProperties properties = new RetentionCleanupProperties();

        properties.validate();

        assertThat(properties.isEnabled()).isFalse();
        assertThat(properties.getExecutionLease()).isEqualTo(Duration.ofMinutes(2));
    }

    @Test
    @DisplayName("삭제 호출 제한보다 짧은 lease와 잘못된 후보 수는 거절한다")
    void validate_failureUnsafeSettings() {
        RetentionCleanupProperties properties = new RetentionCleanupProperties();
        properties.setExecutionLease(Duration.ofSeconds(60));

        assertThatThrownBy(properties::validate).isInstanceOf(RecordingException.class);
        properties.setExecutionLease(Duration.ofMinutes(2));
        properties.setCandidateLimit(0);
        assertThatThrownBy(properties::validate).isInstanceOf(RecordingException.class);
        properties.setCandidateLimit(20);
        properties.setUploadQuiescence(Duration.ZERO);
        assertThatThrownBy(properties::validate).isInstanceOf(RecordingException.class);
    }
}
