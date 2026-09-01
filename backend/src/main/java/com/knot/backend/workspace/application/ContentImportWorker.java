package com.knot.backend.workspace.application;

import com.knot.backend.workspace.application.dto.result.ClaimedContentImportRun;
import com.knot.backend.workspace.application.dto.result.CollectedPage;
import com.knot.backend.workspace.application.dto.result.ContentCollectionResult;
import com.knot.backend.workspace.domain.ContentSourceConnection;
import com.knot.backend.workspace.domain.ContentSourceConnectionRepository;
import com.knot.backend.workspace.domain.ImportedPage;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ContentImportWorker {
    private final ContentImportRunLifecycleService lifecycleService;
    private final ContentSourceConnectionRepository connectionRepository;
    private final ContentSourceSecretProtector secretProtector;
    private final ContentSourceCollector contentCollector;
    private final ContentImportSnapshotStagingService stagingService;
    private final ContentImportPublicationService publicationService;
    private final ContentImportWorkerObserver observer;
    private final ContentImportHeartbeatLease heartbeatLease;

    public boolean processNext() {
        Optional<ClaimedContentImportRun> claimedImportRun = lifecycleService.claimNext();
        if (claimedImportRun.isEmpty()) {
            return false;
        }
        ClaimedContentImportRun importRun = claimedImportRun.orElseThrow();
        observer.claimed(
                importRun.importRunId(),
                importRun.workspaceId()
        );
        ContentImportHeartbeatLease.Handle heartbeatHandle;
        try {
            heartbeatHandle = heartbeatLease.start(
                    importRun.importRunId(),
                    importRun.workspaceId()
            );
        } catch (RuntimeException ignored) {
            fail(
                    importRun,
                    ContentImportFailureCategory.STORAGE
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
                    ContentImportFailureCategory.STORAGE
            );
        }
        return true;
    }

    private void process(
            ClaimedContentImportRun importRun,
            ContentImportHeartbeatLease.Handle heartbeatHandle
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
                    ContentImportFailureCategory.STORAGE
            );
            return;
        }
        if (connection == null) {
            fail(
                    importRun,
                    ContentImportFailureCategory.CREDENTIAL
            );
            return;
        }

        String accessCredential;
        try {
            accessCredential = secretProtector.decrypt(
                    importRun.workspaceId(),
                    connection.getProvider(),
                    ContentSourceCredentialKind.ACCESS_CREDENTIAL,
                    connection.getAccessCredentialCiphertext()
            );
        } catch (RuntimeException ignored) {
            fail(
                    importRun,
                    ContentImportFailureCategory.CREDENTIAL
            );
            return;
        }

        ContentCollectionResult collectionResult;
        try {
            collectionResult = contentCollector.collect(accessCredential);
        } catch (RuntimeException ignored) {
            fail(
                    importRun,
                    ContentImportFailureCategory.COLLECTION
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
                    ContentImportFailureCategory.COLLECTION
            );
            return;
        }
        List<CollectedPage> pages = collectionResult.pages();
        if (pages.isEmpty()) {
            fail(
                    importRun,
                    ContentImportFailureCategory.EMPTY_RESULT
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
                    ContentImportFailureCategory.STORAGE
            );
            return;
        }

        try {
            publicationService.publish(importRun.importRunId());
        } catch (RuntimeException ignored) {
            fail(
                    importRun,
                    ContentImportFailureCategory.PUBLICATION
            );
            return;
        }
        observer.completed(
                importRun.importRunId(),
                importRun.workspaceId(),
                pages.size()
        );
    }

    private boolean isValidCollection(List<CollectedPage> pages) {
        Set<String> visitedPageIds = new HashSet<>();
        for (int index = 0; index < pages.size(); index++) {
            CollectedPage page = pages.get(index);
            if (!isValidPage(
                    page,
                    index,
                    visitedPageIds
            )) {
                return false;
            }
            visitedPageIds.add(page.externalPageId());
        }
        return true;
    }

    private boolean isValidPage(
            CollectedPage page,
            int expectedPosition,
            Set<String> visitedPageIds
    ) {
        if (page == null || isInvalidExternalId(page.externalPageId()) || page.title() == null
                || page.markdownContent() == null || page.position() != expectedPosition || page.sourceUrl() == null
                || page.sourceUrl()
                        .isBlank()
                || visitedPageIds.contains(page.externalPageId())) {
            return false;
        }
        String parentPageId = page.parentExternalPageId();
        return parentPageId == null || !isInvalidExternalId(parentPageId) && visitedPageIds.contains(parentPageId);
    }

    private boolean isInvalidExternalId(String externalId) {
        return externalId == null || externalId.isBlank()
                || externalId.length() > ImportedPage.MAX_EXTERNAL_PAGE_ID_LENGTH;
    }

    private void stage(
            ClaimedContentImportRun importRun,
            List<CollectedPage> pages
    ) {
        stagingService.prepare(
                importRun.importRunId(),
                importRun.workspaceId(),
                pages.size()
        );
        for (CollectedPage page : pages) {
            Long storedPageId = stagingService.stagePage(
                    importRun.importRunId(),
                    importRun.workspaceId(),
                    page.externalPageId(),
                    page.parentExternalPageId(),
                    page.title(),
                    page.markdownContent(),
                    page.position(),
                    page.sourceUrl()
            );
            if (storedPageId == null || storedPageId <= 0) {
                throw new IllegalStateException("가져온 Page staging 결과가 올바르지 않습니다");
            }
        }
    }

    private void fail(
            ClaimedContentImportRun importRun,
            ContentImportFailureCategory category
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
