package com.knot.backend.workspace.infrastructure.notion.worker;

import com.knot.backend.workspace.application.ContentSourceSecretProtector;
import com.knot.backend.workspace.application.ContentSourceCollector;
import com.knot.backend.workspace.application.ContentImportHeartbeatLease;
import com.knot.backend.workspace.application.ContentImportPublicationService;
import com.knot.backend.workspace.application.ContentImportRunLifecycleService;
import com.knot.backend.workspace.application.ContentImportSnapshotStagingService;
import com.knot.backend.workspace.application.ContentImportSearchIndexer;
import com.knot.backend.workspace.application.ContentImportStaleRecoveryService;
import com.knot.backend.workspace.application.ContentImportWorker;
import com.knot.backend.workspace.application.ContentImportWorkerObserver;
import com.knot.backend.workspace.domain.ContentSourceConnectionRepository;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = {"notion.oauth.enabled", "notion.import.worker.enabled"}, havingValue = "true")
@EnableConfigurationProperties(NotionImportWorkerProperties.class)
@EnableScheduling
public class NotionImportWorkerConfig {

    @Bean
    public ContentImportWorker notionImportWorker(
            ContentImportRunLifecycleService lifecycleService,
            ContentSourceConnectionRepository connectionRepository,
            ContentSourceSecretProtector secretProtector,
            ContentSourceCollector contentCollector,
            ContentImportSnapshotStagingService stagingService,
            ContentImportPublicationService publicationService,
            ContentImportSearchIndexer searchIndexer,
            ContentImportWorkerObserver observer,
            ContentImportHeartbeatLease heartbeatLease
    ) {
        return new ContentImportWorker(
                lifecycleService,
                connectionRepository,
                secretProtector,
                contentCollector,
                stagingService,
                publicationService,
                searchIndexer,
                observer,
                heartbeatLease
        );
    }

    @Bean(destroyMethod = "shutdown")
    public ScheduledExecutorService notionImportHeartbeatScheduler() {
        return Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(
                    runnable,
                    "notion-import-heartbeat"
            );
            thread.setDaemon(true);
            return thread;
        });
    }

    @Bean
    public ContentImportHeartbeatLease notionImportHeartbeatLease(
            ContentImportRunLifecycleService lifecycleService,
            ContentImportWorkerObserver observer,
            NotionImportWorkerProperties properties,
            ScheduledExecutorService notionImportHeartbeatScheduler
    ) {
        return new ScheduledNotionImportHeartbeatLease(
                lifecycleService,
                observer,
                properties,
                notionImportHeartbeatScheduler
        );
    }

    @Bean
    public NotionImportWorkerScheduler notionImportWorkerScheduler(
            ContentImportStaleRecoveryService staleRecoveryService,
            ContentImportWorker worker,
            ContentImportWorkerObserver observer,
            NotionImportWorkerProperties properties
    ) {
        return new NotionImportWorkerScheduler(
                staleRecoveryService,
                worker,
                observer,
                properties
        );
    }
}
