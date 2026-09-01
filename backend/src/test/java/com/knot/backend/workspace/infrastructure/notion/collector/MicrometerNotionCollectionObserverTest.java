package com.knot.backend.workspace.infrastructure.notion.collector;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MicrometerNotionCollectionObserverTest {

    @DisplayName("건너뛴 block과 property를 종류별 metric으로 기록한다")
    @Test
    void record_success() {
        // given
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MicrometerNotionCollectionObserver observer = new MicrometerNotionCollectionObserver(registry);

        // when
        observer.recordSkippedBlock(
                "unsupported<Block>",
                2
        );
        observer.recordSkippedProperty(
                "relation",
                3
        );

        // then
        assertThat(
                registry.get(MicrometerNotionCollectionObserver.SKIPPED_ELEMENT_METRIC)
                        .tags(
                                "kind",
                                "block",
                                "type",
                                "unsupported_block_"
                        )
                        .counter()
                        .count()
        ).isEqualTo(2);
        assertThat(
                registry.get(MicrometerNotionCollectionObserver.SKIPPED_ELEMENT_METRIC)
                        .tags(
                                "kind",
                                "property",
                                "type",
                                "relation"
                        )
                        .counter()
                        .count()
        ).isEqualTo(3);
    }
}
