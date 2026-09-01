package com.knot.backend.workspace.application;

import com.knot.backend.workspace.application.dto.result.ClaimedNotionImportRun;
import com.knot.backend.workspace.application.dto.result.CollectedNotionPage;
import com.knot.backend.workspace.application.dto.result.NotionCollectionResult;
import com.knot.backend.workspace.domain.ContentSourceConnection;
import com.knot.backend.workspace.domain.ContentSourceConnectionRepository;
import com.knot.backend.workspace.domain.ContentSourceProvider;
import com.knot.backend.workspace.domain.NotionPage;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class NotionImportWorker {
    private final NotionImportRunLifecycleService lifecycleService;
    private final ContentSourceConnectionRepository connectionRepository;
    private final ContentSourceSecretProtector secretProtector;
    private final NotionContentCollector contentCollector;
    private final NotionImportSnapshotStagingService stagingService;
    private final NotionImportPublicationService publicationService;
    private final NotionImportWorkerObserver observer;
    private final NotionImportHeartbeatLease heartbeatLease;

    public boolean processNext() {
        Optional<ClaimedNotionImportRun> claimedImportRun = lifecycleService.claimNext();
        if (claimedImportRun.isEmpty()) {
            return false;
        }
        ClaimedNotionImportRun importRun = claimedImportRun.orElseThrow();
        observer.claimed(
                importRun.importRunId(),
                importRun.workspaceId()
        );
        NotionImportHeartbeatLease.Handle heartbeatHandle;
        try {
            heartbeatHandle = heartbeatLease.start(
                    importRun.importRunId(),
                    importRun.workspaceId()
            );
        } catch (RuntimeException ignored) {
            fail(
                    importRun,
                    NotionImportFailureCategory.STORAGE
            );
            return true;
        }
        try (heartbeatHandle) {
            process(
                    importRun,
                    heartbeatHandle
            );
        } catch (RuntimeException ignored) {
            fail(
                    importRun,
                    NotionImportFailureCategory.STORAGE
            );
        }
        return true;
    }

    private void process(
            ClaimedNotionImportRun importRun,
            NotionImportHeartbeatLease.Handle heartbeatHandle
    ) {
        ContentSourceConnection connection;
        try {
            connection = connectionRepository.findByIdAndWorkspaceId(
                    importRun.contentSourceConnectionId(),
                    importRun.workspaceId()
            )
                    .orElse(null);
        } catch (RuntimeException ignored) {
            fail(
                    importRun,
                    NotionImportFailureCategory.STORAGE
            );
            return;
        }
        if (connection == null || connection.getProvider() != ContentSourceProvider.NOTION) {
            fail(
                    importRun,
                    NotionImportFailureCategory.CREDENTIAL
            );
            return;
        }

        String accessCredential;
        try {
            accessCredential = secretProtector.decrypt(
                    importRun.workspaceId(),
                    ContentSourceProvider.NOTION,
                    ContentSourceCredentialKind.ACCESS_CREDENTIAL,
                    connection.getAccessCredentialCiphertext()
            );
        } catch (RuntimeException ignored) {
            fail(
                    importRun,
                    NotionImportFailureCategory.CREDENTIAL
            );
            return;
        }

        NotionCollectionResult collectionResult;
        try {
            collectionResult = contentCollector.collect(accessCredential);
        } catch (RuntimeException ignored) {
            fail(
                    importRun,
                    NotionImportFailureCategory.COLLECTION
            );
            return;
        }

        if (!heartbeatHandle.isActive()) {
            return;
        }

        if (collectionResult == null || collectionResult.pages() == null
                || !isValidCollection(collectionResult.pages())) {
            fail(
                    importRun,
                    NotionImportFailureCategory.COLLECTION
            );
            return;
        }
        List<CollectedNotionPage> pages = collectionResult.pages();
        if (pages.isEmpty()) {
            fail(
                    importRun,
                    NotionImportFailureCategory.EMPTY_RESULT
            );
            return;
        }

        try {
            stage(
                    importRun,
                    pages
            );
        } catch (RuntimeException ignored) {
            fail(
                    importRun,
                    NotionImportFailureCategory.STORAGE
            );
            return;
        }

        try {
            publicationService.publish(importRun.importRunId());
        } catch (RuntimeException ignored) {
            fail(
                    importRun,
                    NotionImportFailureCategory.PUBLICATION
            );
            return;
        }
        observer.completed(
                importRun.importRunId(),
                importRun.workspaceId(),
                pages.size()
        );
    }

    private boolean isValidCollection(List<CollectedNotionPage> pages) {
        Set<String> visitedPageIds = new HashSet<>();
        for (int index = 0; index < pages.size(); index++) {
            CollectedNotionPage page = pages.get(index);
            if (!isValidPage(
                    page,
                    index,
                    visitedPageIds
            )) {
                return false;
            }
            visitedPageIds.add(page.notionPageId());
        }
        return true;
    }

    private boolean isValidPage(
            CollectedNotionPage page,
            int expectedPosition,
            Set<String> visitedPageIds
    ) {
        if (page == null || isInvalidExternalId(page.notionPageId()) || page.title() == null
                || page.markdownContent() == null || page.position() != expectedPosition || page.notionUrl() == null
                || page.notionUrl()
                        .isBlank()
                || visitedPageIds.contains(page.notionPageId())) {
            return false;
        }
        String parentPageId = page.parentNotionPageId();
        return parentPageId == null || !isInvalidExternalId(parentPageId) && visitedPageIds.contains(parentPageId);
    }

    private boolean isInvalidExternalId(String externalId) {
        return externalId == null || externalId.isBlank() || externalId.length() > NotionPage.MAX_NOTION_PAGE_ID_LENGTH;
    }

    private void stage(
            ClaimedNotionImportRun importRun,
            List<CollectedNotionPage> pages
    ) {
        stagingService.prepare(
                importRun.importRunId(),
                importRun.workspaceId(),
                pages.size()
        );
        Map<String, Long> storedPageIds = new HashMap<>();
        for (CollectedNotionPage page : pages) {
            Long parentPageId = storedPageIds.get(page.parentNotionPageId());
            Long storedPageId = stagingService.stagePage(
                    importRun.importRunId(),
                    importRun.workspaceId(),
                    page.notionPageId(),
                    parentPageId,
                    page.title(),
                    page.markdownContent(),
                    page.position(),
                    page.notionUrl()
            );
            if (storedPageId == null || storedPageId <= 0) {
                throw new IllegalStateException("Notion Page staging 결과가 올바르지 않습니다");
            }
            storedPageIds.put(
                    page.notionPageId(),
                    storedPageId
            );
        }
    }

    private void fail(
            ClaimedNotionImportRun importRun,
            NotionImportFailureCategory category
    ) {
        boolean failed = lifecycleService.fail(importRun.importRunId());
        if (failed) {
            observer.failed(
                    importRun.importRunId(),
                    importRun.workspaceId(),
                    category
            );
        }
    }
}
