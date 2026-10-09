package com.knot.backend.global.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.knot.backend.document.domain.DocumentException;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class DocumentGenerationWorkerConfigTest {

    @Test
    @DisplayName("기본 설정에서는 실행기를 등록하지 않고 명시적으로 활성화할 때만 생성한다")
    void configuration_success_optIn() {
        // given
        ApplicationContextRunner context = new ApplicationContextRunner()
                .withUserConfiguration(DocumentGenerationWorkerConfig.class);
        // when & then
        context.run(application -> assertThat(application).doesNotHaveBean(ThreadPoolTaskExecutor.class));
        context.withPropertyValues(
                "knot.document-generation.worker.enabled=true",
                "knot.llm.enabled=true"
        )
                .run(application -> assertThat(application).hasSingleBean(ThreadPoolTaskExecutor.class));
        context.withPropertyValues(
                "knot.document-generation.worker.enabled=true",
                "knot.llm.enabled=false"
        )
                .run(application -> assertThat(application).hasFailed());
    }

    @Test
    @DisplayName("LLM을 사용할 수 있을 때 동시 두 개·대기열 없는 실행기를 구성한다")
    void executor_success_boundedCapacity() {
        // given
        LlmProperties llm = new LlmProperties();
        llm.setEnabled(true);
        // when
        ThreadPoolTaskExecutor executor = new DocumentGenerationWorkerConfig().documentGenerationExecutor(
                new DocumentGenerationWorkerProperties(),
                llm
        );
        // then
        assertThat(executor.getCorePoolSize()).isEqualTo(2);
        assertThat(executor.getMaxPoolSize()).isEqualTo(2);
        assertThat(executor.getQueueCapacity()).isZero();
    }

    @Test
    @DisplayName("LLM이 비활성일 때 실행기를 켜지 못한다")
    void executor_failure_missingLlm() {
        // when & then
        assertThatThrownBy(
                () -> new DocumentGenerationWorkerConfig().documentGenerationExecutor(
                        new DocumentGenerationWorkerProperties(),
                        new LlmProperties()
                )
        ).isInstanceOf(DocumentException.class);
    }

    @Test
    @DisplayName("HTTP timeout보다 짧은 실행 기한은 거절한다")
    void validate_failure_shortLease() {
        // given
        LlmProperties llm = new LlmProperties();
        llm.setEnabled(true);
        DocumentGenerationWorkerProperties properties = new DocumentGenerationWorkerProperties();
        properties.setExecutionLease(Duration.ofSeconds(120));
        // when & then
        assertThatThrownBy(() -> properties.validate(llm)).isInstanceOf(DocumentException.class);
    }

    @Test
    @DisplayName("음수 backoff와 무제한 동시성을 거절한다")
    void validate_failure_invalidCapacityAndBackoff() {
        // given
        LlmProperties llm = new LlmProperties();
        llm.setEnabled(true);
        DocumentGenerationWorkerProperties properties = new DocumentGenerationWorkerProperties();
        properties.setConcurrency(0);
        // when & then
        assertThatThrownBy(() -> properties.validate(llm)).isInstanceOf(DocumentException.class);
        properties.setConcurrency(2);
        properties.setAutomaticRetryBackoff(Duration.ofSeconds(-1));
        assertThatThrownBy(() -> properties.validate(llm)).isInstanceOf(DocumentException.class);
    }
}
