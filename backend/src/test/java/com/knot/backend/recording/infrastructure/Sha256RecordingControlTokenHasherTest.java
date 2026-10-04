package com.knot.backend.recording.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.knot.backend.recording.application.dto.command.RecordingStartCommand;
import com.knot.backend.recording.presentation.dto.request.RecordingStartRequest;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class Sha256RecordingControlTokenHasherTest {
    private final Sha256RecordingControlTokenHasher hasher = new Sha256RecordingControlTokenHasher();

    @Test
    @DisplayName("동일 증명은 같은 해시를 만들고 다른 증명은 다른 해시를 만든다")
    void hash_success_stableAndDistinct() {
        // given
        String token = "A".repeat(43);
        String originalHash = hasher.hash(token);
        String otherHash = hasher.hash("B".repeat(42) + "A");

        // when
        String replayHash = hasher.hash(token);

        // then
        assertThat(replayHash).isEqualTo(originalHash)
                .matches("[0-9a-f]{64}")
                .isNotEqualTo(token)
                .isNotEqualTo(otherHash);
    }

    @Test
    @DisplayName("요청과 명령의 문자열 표현에 제어 비밀값을 노출하지 않는다")
    void toString_success_redactsControlToken() {
        // given
        String token = "A".repeat(43);
        RecordingStartRequest request = new RecordingStartRequest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                token
        );
        RecordingStartCommand command = request.toCommand();

        // when
        String description = request.toString() + command.toString();

        // then
        assertThat(description).contains("REDACTED")
                .doesNotContain(token);
    }
}
