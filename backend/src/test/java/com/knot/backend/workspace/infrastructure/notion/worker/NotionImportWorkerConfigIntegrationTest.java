package com.knot.backend.workspace.infrastructure.notion.worker;

import static org.assertj.core.api.Assertions.assertThat;

import com.knot.backend.testsupport.TestApplicationProperties;
import com.knot.backend.testsupport.TestcontainersConfiguration;
import com.knot.backend.workspace.application.NotionContentCollector;
import com.knot.backend.workspace.application.NotionImportWorker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.TestPropertySource;

@Tag("integration")
@Import(TestcontainersConfiguration.class)
@TestApplicationProperties
@TestPropertySource(properties = {"notion.oauth.enabled=true", "notion.import.worker.enabled=true",
        "notion.oauth.client-id=test-notion-client-id", "notion.oauth.client-secret=test-notion-client-secret",
        "notion.oauth.callback-uri=https://api.example.com/api/v1/notion/oauth/callback",
        "notion.oauth.state-hash-key=bm90aW9uLXN0YXRlLWhhc2gta2V5LTAwMDAwMDAwMDA",
        "notion.oauth.encryption-keys.v1=bm90aW9uLWVuY3J5cHRpb24ta2V5LTAwMDAwMDAwMDA",
        "notion.import.worker.poll-delay=PT1H"})
@SpringBootTest
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class NotionImportWorkerConfigIntegrationTest {
    private final ApplicationContext applicationContext;

    NotionImportWorkerConfigIntegrationTest(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @DisplayName("Notion OAuth와 Import worker를 활성화하면 실제 수집기와 polling 작업자를 조립한다")
    @Test
    void config_success_enabled() {
        // given

        // when
        NotionImportWorker worker = applicationContext.getBean(NotionImportWorker.class);
        NotionContentCollector collector = applicationContext.getBean(NotionContentCollector.class);
        NotionImportWorkerScheduler scheduler = applicationContext.getBean(NotionImportWorkerScheduler.class);

        // then
        assertThat(worker).isNotNull();
        assertThat(collector).isNotNull();
        assertThat(scheduler).isNotNull();
    }
}
