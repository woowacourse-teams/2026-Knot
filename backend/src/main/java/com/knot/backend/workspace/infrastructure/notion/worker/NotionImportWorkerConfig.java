package com.knot.backend.workspace.infrastructure.notion.worker;

import com.knot.backend.workspace.application.ContentSourceSecretProtector;
import com.knot.backend.workspace.application.NotionContentCollector;
import com.knot.backend.workspace.application.NotionImportHeartbeatLease;
import com.knot.backend.workspace.application.NotionImportPublicationService;
import com.knot.backend.workspace.application.NotionImportRunLifecycleService;
import com.knot.backend.workspace.application.NotionImportSnapshotStagingService;
import com.knot.backend.workspace.application.NotionImportStaleRecoveryService;
import com.knot.backend.workspace.application.NotionImportWorker;
import com.knot.backend.workspace.application.NotionImportWorkerObserver;
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
    public NotionImportWorker notionImportWorker(
            NotionImportRunLifecycleService lifecycleService,
            ContentSourceConnectionRepository connectionRepository,
            ContentSourceSecretProtector secretProtector,
            NotionContentCollector contentCollector,
            NotionImportSnapshotStagingService stagingService,
            NotionImportPublicationService publicationService,
            NotionImportWorkerObserver observer,
            NotionImportHeartbeatLease heartbeatLease
    ) {
        return new NotionImportWorker(
                lifecycleService,
                connectionRepository,
                secretProtector,
                contentCollector,
                stagingService,
                publicationService,
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
    public NotionImportHeartbeatLease notionImportHeartbeatLease(
            NotionImportRunLifecycleService lifecycleService,
            NotionImportWorkerObserver observer,
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
            NotionImportStaleRecoveryService staleRecoveryService,
            NotionImportWorker worker,
            NotionImportWorkerObserver observer,
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
