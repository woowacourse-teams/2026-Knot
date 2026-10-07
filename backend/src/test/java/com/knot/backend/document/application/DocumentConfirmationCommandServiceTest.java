package com.knot.backend.document.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.knot.backend.document.application.dto.result.DocumentConfirmationOverviewResult;
import com.knot.backend.document.application.dto.result.DocumentConfirmationResult;
import com.knot.backend.document.application.dto.result.DocumentConfirmationSummaryResult;
import com.knot.backend.document.domain.Document;
import com.knot.backend.document.domain.DocumentConfirmation;
import com.knot.backend.document.domain.DocumentConfirmationRepository;
import com.knot.backend.document.domain.DocumentErrorCode;
import com.knot.backend.document.domain.DocumentException;
import com.knot.backend.document.domain.DocumentRepository;
import com.knot.backend.document.domain.DocumentStatus;
import com.knot.backend.workspace.domain.Workspace;
import com.knot.backend.workspace.domain.WorkspaceException;
import com.knot.backend.workspace.domain.WorkspaceMemberRepository;
import com.knot.backend.workspace.domain.WorkspaceRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class DocumentConfirmationCommandServiceTest {
    private static final Instant NOW = Instant.parse("2026-10-06T00:01:00.123456Z");
    private final WorkspaceRepository workspaces = mock(WorkspaceRepository.class);
    private final WorkspaceMemberRepository members = mock(WorkspaceMemberRepository.class);
    private final DocumentRepository documents = mock(DocumentRepository.class);
    private final DocumentConfirmationRepository confirmations = mock(DocumentConfirmationRepository.class);
    private final DocumentConfirmationQuery query = mock(DocumentConfirmationQuery.class);
    private final DocumentConfirmationCommandService service = new DocumentConfirmationCommandService(
            workspaces,
            members,
            documents,
            confirmations,
            query,
            Clock.fixed(
                    NOW,
                    ZoneOffset.UTC
            )
    );

    @Test
    @DisplayName("Workspace 잠금과 권한 검사를 거쳐 최초 확인을 반영한 집계를 반환한다")
    void confirm_success() {
        // given
        Document document = draft();
        DocumentConfirmation confirmation = stubTarget(
                document,
                1
        );

        // when
        DocumentConfirmationResult result = service.confirm(
                1,
                2,
                3
        );

        // then
        assertThat(result.confirmedAt()).isEqualTo(NOW);
        assertThat(result.documentStatus()).isEqualTo(DocumentStatus.DRAFT);
        assertThat(result.archivedAt()).isNull();
        assertThat(
                result.confirmationSummary()
                        .pendingCount()
        ).isEqualTo(1);
        assertThat(confirmation.getConfirmedAt()).isEqualTo(NOW);
        InOrder order = inOrder(
                workspaces,
                members,
                documents,
                confirmations,
                query
        );
        order.verify(workspaces)
                .findByIdForUpdate(1L);
        order.verify(members)
                .existsByWorkspaceIdAndMemberId(
                        1L,
                        2L
                );
        order.verify(documents)
                .findByWorkspaceIdAndIdForUpdate(
                        1,
                        3
                );
        order.verify(confirmations)
                .findByDocumentIdAndMemberId(
                        3,
                        2
                );
        order.verify(confirmations)
                .flush();
        order.verify(query)
                .findSummary(
                        1,
                        3,
                        2
                );
        order.verify(documents)
                .flush();
    }

    @Test
    @DisplayName("마지막 필수 대상의 확인은 보관 상태와 시각을 함께 반환한다")
    void confirm_success_lastTarget() {
        // given
        Document document = draft();
        stubTarget(
                document,
                0
        );

        // when
        DocumentConfirmationResult result = service.confirm(
                1,
                2,
                3
        );

        // then
        assertThat(result.documentStatus()).isEqualTo(DocumentStatus.ARCHIVED);
        assertThat(result.archivedAt()).isEqualTo(NOW);
        assertThat(document.getArchivedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("반복 확인은 최초 확인·보관 시각을 유지하고 현재 집계를 반환한다")
    void confirm_success_repeatedRequest() {
        // given
        Document document = draft();
        Instant first = NOW.minusSeconds(30);
        document.archive(first);
        DocumentConfirmation confirmation = stubTarget(
                document,
                0
        );
        confirmation.confirm(first);

        // when
        DocumentConfirmationResult result = service.confirm(
                1,
                2,
                3
        );

        // then
        assertThat(result.confirmedAt()).isEqualTo(first);
        assertThat(result.archivedAt()).isEqualTo(first);
        assertThat(
                result.confirmationSummary()
                        .pendingCount()
        ).isZero();
    }

    @Test
    @DisplayName("없는 Workspace와 비멤버는 문서에 접근하기 전에 거절한다")
    void confirm_failure_workspaceAccess() {
        // when & then
        assertThatThrownBy(
                () -> service.confirm(
                        1,
                        2,
                        3
                )
        ).isInstanceOf(WorkspaceException.class);
        verifyNoInteractions(
                documents,
                confirmations,
                query
        );
    }

    @Test
    @DisplayName("탈퇴자는 Workspace 잠금 후 현재 권한 검사에서 거절한다")
    void confirm_failure_inactiveMember() {
        // given
        when(workspaces.findByIdForUpdate(1L)).thenReturn(Optional.of(mock(Workspace.class)));

        // when & then
        assertThatThrownBy(
                () -> service.confirm(
                        1,
                        2,
                        3
                )
        ).isInstanceOf(WorkspaceException.class);
        verifyNoInteractions(
                documents,
                confirmations,
                query
        );
    }

    @Test
    @DisplayName("Workspace 범위에 없는 문서는 404 오류로 거절한다")
    void confirm_failure_missingDocument() {
        // given
        stubAccess();

        // when & then
        assertThatThrownBy(
                () -> service.confirm(
                        1,
                        2,
                        3
                )
        ).isInstanceOf(DocumentException.class)
                .extracting("errorCode")
                .isEqualTo(DocumentErrorCode.DOCUMENT_NOT_FOUND);
        verifyNoInteractions(
                confirmations,
                query
        );
    }

    @Test
    @DisplayName("이후 가입한 비대상 멤버의 확인은 409이며 대상을 새로 만들지 않는다")
    void confirm_failure_notRequired() {
        // given
        stubAccess();
        when(
                documents.findByWorkspaceIdAndIdForUpdate(
                        1,
                        3
                )
        ).thenReturn(Optional.of(draft()));

        // when & then
        assertThatThrownBy(
                () -> service.confirm(
                        1,
                        2,
                        3
                )
        ).isInstanceOf(DocumentException.class)
                .extracting("errorCode")
                .isEqualTo(DocumentErrorCode.CONFIRMATION_NOT_REQUIRED);
        verify(
                confirmations,
                never()
        ).flush();
        verifyNoInteractions(query);
    }

    private void stubAccess() {
        when(workspaces.findByIdForUpdate(1L)).thenReturn(Optional.of(mock(Workspace.class)));
        when(
                members.existsByWorkspaceIdAndMemberId(
                        1L,
                        2L
                )
        ).thenReturn(true);
    }

    private DocumentConfirmation stubTarget(
            Document document,
            int pendingCount
    ) {
        stubAccess();
        DocumentConfirmation confirmation = DocumentConfirmation.require(
                3,
                2
        );
        when(
                documents.findByWorkspaceIdAndIdForUpdate(
                        1,
                        3
                )
        ).thenReturn(Optional.of(document));
        when(
                confirmations.findByDocumentIdAndMemberId(
                        3,
                        2
                )
        ).thenReturn(Optional.of(confirmation));
        when(
                query.findSummary(
                        1,
                        3,
                        2
                )
        ).thenReturn(
                Optional.of(
                        new DocumentConfirmationOverviewResult(
                                3,
                                new DocumentConfirmationSummaryResult(
                                        1,
                                        pendingCount,
                                        0
                                ),
                                true
                        )
                )
        );
        return confirmation;
    }

    private Document draft() {
        return Document.createDraft(
                1,
                4,
                5,
                6,
                "정책",
                "제목",
                null,
                "본문",
                NOW.minusSeconds(60)
        );
    }
}
