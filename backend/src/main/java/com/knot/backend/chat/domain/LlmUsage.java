package com.knot.backend.chat.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.util.Objects;
import lombok.Getter;

/**
 * 서버가 모델을 불러 답변을 만들 때 쓴 토큰 사용량(데스크톱 기획서 6.2 계측 행, 로드맵 B2).
 *
 * <p>ASSISTANT 메시지와 같은 트랜잭션에 저장되며, 값이 있는 것은 서버가 모델을 부른 답변뿐이다. 안내 문구 폴백과
 * {@code generated_by=CLIENT} 턴에는 사용량이 없다(모든 컬럼 NULL).
 *
 * <p>사용량은 부수 기록이므로 답변 저장을 막지 않는다. 어댑터가 준 값이 음수면 0으로, 모델 이름이 컬럼 길이를 넘으면
 * 잘라서 담는다.
 */
@Getter
@Embeddable
public class LlmUsage {
    static final int MODEL_MAX_LENGTH = 100;

    @Column(name = "llm_model", length = MODEL_MAX_LENGTH)
    private String model;

    @Column(name = "input_tokens")
    private Long inputTokens;

    @Column(name = "output_tokens")
    private Long outputTokens;

    @Column(name = "cache_read_input_tokens")
    private Long cacheReadInputTokens;

    @Column(name = "cache_creation_input_tokens")
    private Long cacheCreationInputTokens;

    protected LlmUsage() {}

    private LlmUsage(
            String model,
            long inputTokens,
            long outputTokens,
            long cacheReadInputTokens,
            long cacheCreationInputTokens
    ) {
        this.model = truncate(model);
        this.inputTokens = clamp(inputTokens);
        this.outputTokens = clamp(outputTokens);
        this.cacheReadInputTokens = clamp(cacheReadInputTokens);
        this.cacheCreationInputTokens = clamp(cacheCreationInputTokens);
    }

    public static LlmUsage of(
            String model,
            long inputTokens,
            long outputTokens,
            long cacheReadInputTokens,
            long cacheCreationInputTokens
    ) {
        return new LlmUsage(
                model,
                inputTokens,
                outputTokens,
                cacheReadInputTokens,
                cacheCreationInputTokens
        );
    }

    private static String truncate(String model) {
        if (model == null || model.isBlank()) {
            return null;
        }
        if (model.length() <= MODEL_MAX_LENGTH) {
            return model;
        }
        return model.substring(
                0,
                MODEL_MAX_LENGTH
        );
    }

    private static long clamp(long tokens) {
        return Math.max(
                tokens,
                0
        );
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof LlmUsage usage)) {
            return false;
        }
        return Objects.equals(
                model,
                usage.model
        ) && Objects.equals(
                inputTokens,
                usage.inputTokens
        ) && Objects.equals(
                outputTokens,
                usage.outputTokens
        ) && Objects.equals(
                cacheReadInputTokens,
                usage.cacheReadInputTokens
        ) && Objects.equals(
                cacheCreationInputTokens,
                usage.cacheCreationInputTokens
        );
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                model,
                inputTokens,
                outputTokens,
                cacheReadInputTokens,
                cacheCreationInputTokens
        );
    }
}
