package com.knot.backend.workspace.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.knot.backend.workspace.application.dto.result.ClaimedNotionImportRun;
import com.knot.backend.workspace.application.dto.result.CollectedNotionPage;
import com.knot.backend.workspace.application.dto.result.NotionCollectionResult;
import com.knot.backend.workspace.domain.ContentSourceConnection;
import com.knot.backend.workspace.domain.ContentSourceConnectionRepository;
import com.knot.backend.workspace.domain.ContentSourceProvider;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InOrder;

class NotionImportWorkerTest {
    private static final Long IMPORT_RUN_ID = 1L;
    private static final Long WORKSPACE_ID = 2L;
    private static final Long CONNECTION_ID = 3L;
    private static final String CIPHERTEXT = "encrypted-access-credential";
    private static final String ACCESS_CREDENTIAL = "decrypted-access-credential";
    private static final ClaimedNotionImportRun CLAIMED_IMPORT_RUN = new ClaimedNotionImportRun(
            IMPORT_RUN_ID,
            WORKSPACE_ID,
            CONNECTION_ID
    );

    private final NotionImportRunLifecycleService lifecycleService = mock(NotionImportRunLifecycleService.class);
    private final ContentSourceConnectionRepository connectionRepository = mock(
            ContentSourceConnectionRepository.class
    );
    private final ContentSourceSecretProtector secretProtector = mock(ContentSourceSecretProtector.class);
    private final NotionContentCollector contentCollector = mock(NotionContentCollector.class);
    private final NotionImportSnapshotStagingService stagingService = mock(NotionImportSnapshotStagingService.class);
    private final NotionImportPublicationService publicationService = mock(NotionImportPublicationService.class);
    private final NotionImportWorkerObserver observer = mock(NotionImportWorkerObserver.class);
    private final NotionImportWorker worker = new NotionImportWorker(
            lifecycleService,
            connectionRepository,
            secretProtector,
            contentCollector,
            stagingService,
            publicationService,
            observer
    );

    @DisplayName("PENDING Run이 없으면 외부 자격 증명과 수집기를 사용하지 않는다")
    @Test
    void processNext_success_noPendingRun() {
        // given
        when(lifecycleService.claimNext()).thenReturn(Optional.empty());

        // when
        boolean processed = worker.processNext();

        // then
        assertThat(processed).isFalse();
        verifyNoInteractions(
                connectionRepository,
                secretProtector,
                contentCollector,
                stagingService,
                publicationService,
                observer
        );
    }

    @DisplayName("복호화한 자격 증명으로 수집한 preorder를 저장하고 부모 DB ID를 연결한 뒤 공개한다")
    @Test
    void processNext_success_stagesAndPublishesCompleteSnapshot() {
        // given
        allowClaimAndCredential();
        List<CollectedNotionPage> pages = List.of(
                page(
                        "root",
                        null,
                        "루트",
                        "# 루트",
                        0
                ),
                page(
                        "child",
                        "root",
                        "자식",
                        "자식 본문",
                        1
                )
        );
        when(contentCollector.collect(ACCESS_CREDENTIAL)).thenReturn(new NotionCollectionResult(pages));
        when(
                stagingService.stagePage(
                        IMPORT_RUN_ID,
                        WORKSPACE_ID,
                        "root",
                        null,
                        "루트",
                        "# 루트",
                        0,
                        "https://www.notion.so/root"
                )
        ).thenReturn(10L);
        when(
                stagingService.stagePage(
                        IMPORT_RUN_ID,
                        WORKSPACE_ID,
                        "child",
                        10L,
                        "자식",
                        "자식 본문",
                        1,
                        "https://www.notion.so/child"
                )
        ).thenReturn(11L);

        // when
        boolean processed = worker.processNext();

        // then
        assertThat(processed).isTrue();
        verify(secretProtector).decrypt(
                WORKSPACE_ID,
                ContentSourceProvider.NOTION,
                ContentSourceCredentialKind.ACCESS_CREDENTIAL,
                CIPHERTEXT
        );
        verify(contentCollector).collect(ACCESS_CREDENTIAL);
        InOrder persistenceOrder = inOrder(
                stagingService,
                publicationService
        );
        persistenceOrder.verify(stagingService)
                .prepare(
                        IMPORT_RUN_ID,
                        WORKSPACE_ID,
                        2
                );
        persistenceOrder.verify(stagingService)
                .stagePage(
                        IMPORT_RUN_ID,
                        WORKSPACE_ID,
                        "root",
                        null,
                        "루트",
                        "# 루트",
                        0,
                        "https://www.notion.so/root"
                );
        persistenceOrder.verify(stagingService)
                .stagePage(
                        IMPORT_RUN_ID,
                        WORKSPACE_ID,
                        "child",
                        10L,
                        "자식",
                        "자식 본문",
                        1,
                        "https://www.notion.so/child"
                );
        persistenceOrder.verify(publicationService)
                .publish(IMPORT_RUN_ID);
        verify(observer).completed(
                IMPORT_RUN_ID,
                WORKSPACE_ID,
                2
        );
        verify(
                lifecycleService,
                never()
        ).fail(IMPORT_RUN_ID);
    }

    @DisplayName("수집 결과가 0건이면 Run을 실패 처리하고 저장·공개하지 않는다")
    @Test
    void processNext_failure_emptyCollection() {
        // given
        allowClaimAndCredential();
        when(contentCollector.collect(ACCESS_CREDENTIAL)).thenReturn(new NotionCollectionResult(List.of()));
        allowFailureTransition();

        // when
        boolean processed = worker.processNext();

        // then
        assertThat(processed).isTrue();
        verifyFailure(NotionImportFailureCategory.EMPTY_RESULT);
        verifyNoInteractions(
                stagingService,
                publicationService
        );
    }

    @DisplayName("복호화에 실패하면 평문 자격 증명 없이 Run을 실패 처리한다")
    @Test
    void processNext_failure_credentialDecryption() {
        // given
        ContentSourceConnection connection = allowClaimAndConnection();
        when(
                secretProtector.decrypt(
                        WORKSPACE_ID,
                        ContentSourceProvider.NOTION,
                        ContentSourceCredentialKind.ACCESS_CREDENTIAL,
                        CIPHERTEXT
                )
        ).thenThrow(new IllegalStateException("credential failure"));
        allowFailureTransition();

        // when
        boolean processed = worker.processNext();

        // then
        assertThat(processed).isTrue();
        assertThat(connection.getAccessCredentialCiphertext()).isEqualTo(CIPHERTEXT);
        verifyFailure(NotionImportFailureCategory.CREDENTIAL);
        verifyNoInteractions(
                contentCollector,
                stagingService,
                publicationService
        );
    }

    @DisplayName("연결 조회가 실패하면 Run을 저장 실패로 끝내고 자격 증명을 사용하지 않는다")
    @Test
    void processNext_failure_connectionLookup() {
        // given
        when(lifecycleService.claimNext()).thenReturn(Optional.of(CLAIMED_IMPORT_RUN));
        when(
                connectionRepository.findByIdAndWorkspaceId(
                        CONNECTION_ID,
                        WORKSPACE_ID
                )
        ).thenThrow(new IllegalStateException("storage details"));
        allowFailureTransition();

        // when
        boolean processed = worker.processNext();

        // then
        assertThat(processed).isTrue();
        verifyFailure(NotionImportFailureCategory.STORAGE);
        verifyNoInteractions(
                secretProtector,
                contentCollector,
                stagingService,
                publicationService
        );
    }

    @DisplayName("수집기가 실패하면 외부 오류 원문 없이 Run을 실패 처리한다")
    @Test
    void processNext_failure_collection() {
        // given
        allowClaimAndCredential();
        when(contentCollector.collect(ACCESS_CREDENTIAL))
                .thenThrow(new NotionCollectionException(NotionCollectionFailureType.TEMPORARY));
        allowFailureTransition();

        // when
        boolean processed = worker.processNext();

        // then
        assertThat(processed).isTrue();
        verifyFailure(NotionImportFailureCategory.COLLECTION);
        verifyNoInteractions(
                stagingService,
                publicationService
        );
    }

    @DisplayName("중복 ID, 연속 position과 부모 선행 계약이 깨진 결과는 저장 전에 실패 처리한다")
    @MethodSource("invalidCollectionCases")
    @ParameterizedTest(name = "{0}")
    void processNext_failure_invalidCollection(
            String caseName,
            List<CollectedNotionPage> pages
    ) {
        // given
        allowClaimAndCredential();
        when(contentCollector.collect(ACCESS_CREDENTIAL)).thenReturn(new NotionCollectionResult(pages));
        allowFailureTransition();

        // when
        boolean processed = worker.processNext();

        // then
        assertThat(processed).isTrue();
        verifyFailure(NotionImportFailureCategory.COLLECTION);
        verifyNoInteractions(
                stagingService,
                publicationService
        );
    }

    @DisplayName("Page 중간 저장이 실패하면 Run을 실패 처리하고 publication을 전환하지 않는다")
    @Test
    void processNext_failure_staging() {
        // given
        allowClaimAndCredential();
        CollectedNotionPage page = page(
                "root",
                null,
                "루트",
                "# 루트",
                0
        );
        when(contentCollector.collect(ACCESS_CREDENTIAL)).thenReturn(new NotionCollectionResult(List.of(page)));
        when(
                stagingService.stagePage(
                        IMPORT_RUN_ID,
                        WORKSPACE_ID,
                        "root",
                        null,
                        "루트",
                        "# 루트",
                        0,
                        "https://www.notion.so/root"
                )
        ).thenThrow(new IllegalStateException("storage failure"));
        allowFailureTransition();

        // when
        boolean processed = worker.processNext();

        // then
        assertThat(processed).isTrue();
        verifyFailure(NotionImportFailureCategory.STORAGE);
        verifyNoInteractions(publicationService);
    }

    @DisplayName("publication transaction이 실패하면 Run을 실패 처리하고 완료 metric을 기록하지 않는다")
    @Test
    void processNext_failure_publication() {
        // given
        allowClaimAndCredential();
        CollectedNotionPage page = page(
                "root",
                null,
                "루트",
                "# 루트",
                0
        );
        when(contentCollector.collect(ACCESS_CREDENTIAL)).thenReturn(new NotionCollectionResult(List.of(page)));
        when(
                stagingService.stagePage(
                        IMPORT_RUN_ID,
                        WORKSPACE_ID,
                        "root",
                        null,
                        "루트",
                        "# 루트",
                        0,
                        "https://www.notion.so/root"
                )
        ).thenReturn(10L);
        org.mockito.Mockito.doThrow(new IllegalStateException("publication failure"))
                .when(publicationService)
                .publish(IMPORT_RUN_ID);
        allowFailureTransition();

        // when
        boolean processed = worker.processNext();

        // then
        assertThat(processed).isTrue();
        verifyFailure(NotionImportFailureCategory.PUBLICATION);
        verify(
                observer,
                never()
        ).completed(
                IMPORT_RUN_ID,
                WORKSPACE_ID,
                1
        );
    }

    private void allowClaimAndCredential() {
        ContentSourceConnection connection = allowClaimAndConnection();
        when(
                secretProtector.decrypt(
                        WORKSPACE_ID,
                        ContentSourceProvider.NOTION,
                        ContentSourceCredentialKind.ACCESS_CREDENTIAL,
                        CIPHERTEXT
                )
        ).thenReturn(ACCESS_CREDENTIAL);
        assertThat(connection.getProvider()).isEqualTo(ContentSourceProvider.NOTION);
    }

    private ContentSourceConnection allowClaimAndConnection() {
        when(lifecycleService.claimNext()).thenReturn(Optional.of(CLAIMED_IMPORT_RUN));
        ContentSourceConnection connection = mock(ContentSourceConnection.class);
        when(connection.getProvider()).thenReturn(ContentSourceProvider.NOTION);
        when(connection.getAccessCredentialCiphertext()).thenReturn(CIPHERTEXT);
        when(
                connectionRepository.findByIdAndWorkspaceId(
                        CONNECTION_ID,
                        WORKSPACE_ID
                )
        ).thenReturn(Optional.of(connection));
        return connection;
    }

    private void allowFailureTransition() {
        when(lifecycleService.fail(IMPORT_RUN_ID)).thenReturn(true);
    }

    private void verifyFailure(NotionImportFailureCategory category) {
        verify(lifecycleService).fail(IMPORT_RUN_ID);
        verify(observer).failed(
                IMPORT_RUN_ID,
                WORKSPACE_ID,
                category
        );
    }

    private CollectedNotionPage page(
            String notionPageId,
            String parentNotionPageId,
            String title,
            String markdownContent,
            int position
    ) {
        return new CollectedNotionPage(
                notionPageId,
                parentNotionPageId,
                title,
                markdownContent,
                position,
                "https://www.notion.so/" + notionPageId
        );
    }

    private static Stream<Arguments> invalidCollectionCases() {
        return Stream.of(
                Arguments.of(
                        "missing-parent",
                        List.of(
                                collectedPage(
                                        "child",
                                        "missing-parent",
                                        "자식",
                                        "본문",
                                        0
                                )
                        )
                ),
                Arguments.of(
                        "duplicate-id",
                        List.of(
                                collectedPage(
                                        "duplicate",
                                        null,
                                        "첫 Page",
                                        "첫 본문",
                                        0
                                ),
                                collectedPage(
                                        "duplicate",
                                        null,
                                        "둘째 Page",
                                        "둘째 본문",
                                        1
                                )
                        )
                ),
                Arguments.of(
                        "non-contiguous-position",
                        List.of(
                                collectedPage(
                                        "root",
                                        null,
                                        "루트",
                                        "본문",
                                        1
                                )
                        )
                )
        );
    }

    private static CollectedNotionPage collectedPage(
            String notionPageId,
            String parentNotionPageId,
            String title,
            String markdownContent,
            int position
    ) {
        return new CollectedNotionPage(
                notionPageId,
                parentNotionPageId,
                title,
                markdownContent,
                position,
                "https://www.notion.so/" + notionPageId
        );
    }
}
