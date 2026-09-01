package com.knot.backend.workspace.infrastructure.notion;

import static org.assertj.core.api.Assertions.assertThat;

import com.knot.backend.workspace.application.NotionImportFailureCategory;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MicrometerNotionImportWorkerObserverTest {

    @DisplayName("Import 결과와 실패 범주를 제한된 metric tag로 기록한다")
    @Test
    void observe_success_safeMetrics() {
        // given
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        MicrometerNotionImportWorkerObserver observer = new MicrometerNotionImportWorkerObserver(meterRegistry);

        // when
        observer.claimed(
                1L,
                2L
        );
        observer.completed(
                1L,
                2L,
                3
        );
        observer.failed(
                4L,
                2L,
                NotionImportFailureCategory.COLLECTION
        );
        observer.staleRecovered(1);
        observer.heartbeatFailed(
                5L,
                2L
        );

        // then
        assertThat(
                meterRegistry.get("knot.notion.import.runs")
                        .tag(
                                "outcome",
                                "claimed"
                        )
                        .counter()
                        .count()
        ).isEqualTo(1);
        assertThat(
                meterRegistry.get("knot.notion.import.runs")
                        .tag(
                                "outcome",
                                "completed"
                        )
                        .counter()
                        .count()
        ).isEqualTo(1);
        assertThat(
                meterRegistry.get("knot.notion.import.runs")
                        .tags(
                                "outcome",
                                "failed",
                                "category",
                                "COLLECTION"
                        )
                        .counter()
                        .count()
        ).isEqualTo(1);
        assertThat(
                meterRegistry.get("knot.notion.import.pages")
                        .summary()
                        .totalAmount()
        ).isEqualTo(3);
        assertThat(
                meterRegistry.get("knot.notion.import.stale.recovered")
                        .tag(
                                "previous_status",
                                "RUNNING"
                        )
                        .counter()
                        .count()
        ).isEqualTo(1);
        assertThat(
                meterRegistry.find("knot.notion.import.stale.recovered")
                        .tag(
                                "previous_status",
                                "PENDING"
                        )
                        .counter()
        ).isNull();
        assertThat(
                meterRegistry.get("knot.notion.import.runs")
                        .tag(
                                "outcome",
                                "heartbeat_failed"
                        )
                        .counter()
                        .count()
        ).isEqualTo(1);
    }
}
