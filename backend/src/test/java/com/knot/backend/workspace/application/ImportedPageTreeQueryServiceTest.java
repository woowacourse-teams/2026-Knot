package com.knot.backend.workspace.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.knot.backend.workspace.application.dto.result.ImportedPageTreeItemResult;
import com.knot.backend.workspace.domain.ImportedPageErrorCode;
import com.knot.backend.workspace.domain.ImportedPageException;
import com.knot.backend.workspace.domain.ImportedPageMetadata;
import com.knot.backend.workspace.domain.ImportedPageRepository;
import com.knot.backend.workspace.domain.Workspace;
import com.knot.backend.workspace.domain.WorkspaceErrorCode;
import com.knot.backend.workspace.domain.WorkspaceException;
import com.knot.backend.workspace.domain.WorkspaceMemberRepository;
import com.knot.backend.workspace.domain.WorkspaceRepository;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ImportedPageTreeQueryServiceTest {
    private static final Long WORKSPACE_ID = 1L;
    private static final long MEMBER_ID = 10L;

    private final WorkspaceRepository workspaceRepository = mock(WorkspaceRepository.class);
    private final WorkspaceMemberRepository workspaceMemberRepository = mock(WorkspaceMemberRepository.class);
    private final ImportedPageRepository importedPageRepository = mock(ImportedPageRepository.class);
    private final ImportedPageTreeQueryService service = new ImportedPageTreeQueryService(
            workspaceRepository,
            workspaceMemberRepository,
            importedPageRepository
    );

    @DisplayName("Workspace 멤버에게 발행된 Page를 평면 Tree 계약으로 변환한다")
    @Test
    void findTree_success_mapsPublishedPages() {
        // given
        allowWorkspaceMember();
        ImportedPageMetadata rootPage = importedPage(
                1L,
                null,
                "루트",
                0,
                "https://content.example/pages/root"
        );
        ImportedPageMetadata childPage = importedPage(
                2L,
                1L,
                "자식",
                1,
                "https://content.example/pages/child"
        );
        when(importedPageRepository.findPublishedMetadataByWorkspaceIdOrderByPositionAscIdAsc(WORKSPACE_ID)).thenReturn(
                List.of(
                        rootPage,
                        childPage
                )
        );

        // when
        List<ImportedPageTreeItemResult> result = service.findTree(
                WORKSPACE_ID,
                MEMBER_ID
        );

        // then
        assertThat(result).extracting(
                ImportedPageTreeItemResult::id,
                ImportedPageTreeItemResult::parentId,
                ImportedPageTreeItemResult::title,
                ImportedPageTreeItemResult::position,
                ImportedPageTreeItemResult::sourceUrl
        )
                .containsExactly(
                        tuple(
                                1L,
                                null,
                                "루트",
                                0,
                                "https://content.example/pages/root"
                        ),
                        tuple(
                                2L,
                                1L,
                                "자식",
                                1,
                                "https://content.example/pages/child"
                        )
                );
        verify(importedPageRepository).findPublishedMetadataByWorkspaceIdOrderByPositionAscIdAsc(WORKSPACE_ID);
    }

    @DisplayName("발행된 Page가 없으면 빈 배열을 반환한다")
    @Test
    void findTree_success_emptyPublishedPages() {
        // given
        allowWorkspaceMember();
        when(importedPageRepository.findPublishedMetadataByWorkspaceIdOrderByPositionAscIdAsc(WORKSPACE_ID))
                .thenReturn(List.of());

        // when
        List<ImportedPageTreeItemResult> result = service.findTree(
                WORKSPACE_ID,
                MEMBER_ID
        );

        // then
        assertThat(result).isEmpty();
    }

    @DisplayName("Workspace ID가 양수가 아니면 저장소를 조회하지 않는다")
    @Test
    void findTree_failure_invalidWorkspaceId() {
        // given
        ThrowingCallable action = () -> service.findTree(
                0L,
                MEMBER_ID
        );

        // when
        Throwable thrown = org.assertj.core.api.Assertions.catchThrowable(action);

        // then
        assertThat(thrown).isInstanceOf(WorkspaceException.class)
                .extracting(exception -> ((WorkspaceException) exception).getErrorCode())
                .isEqualTo(WorkspaceErrorCode.INVALID_WORKSPACE_ID);
        verifyNoInteractions(
                workspaceRepository,
                workspaceMemberRepository,
                importedPageRepository
        );
    }

    @DisplayName("Workspace가 없으면 Page와 멤버십을 조회하지 않는다")
    @Test
    void findTree_failure_workspaceNotFound() {
        // given
        when(workspaceRepository.findById(WORKSPACE_ID)).thenReturn(Optional.empty());
        ThrowingCallable action = () -> service.findTree(
                WORKSPACE_ID,
                MEMBER_ID
        );

        // when
        Throwable thrown = org.assertj.core.api.Assertions.catchThrowable(action);

        // then
        assertThat(thrown).isInstanceOf(WorkspaceException.class)
                .extracting(exception -> ((WorkspaceException) exception).getErrorCode())
                .isEqualTo(WorkspaceErrorCode.WORKSPACE_NOT_FOUND);
        verifyNoInteractions(
                workspaceMemberRepository,
                importedPageRepository
        );
    }

    @DisplayName("Workspace 멤버가 아니면 Page를 조회하지 않는다")
    @Test
    void findTree_failure_workspaceAccessDenied() {
        // given
        when(workspaceRepository.findById(WORKSPACE_ID)).thenReturn(Optional.of(mock(Workspace.class)));
        when(
                workspaceMemberRepository.existsByWorkspaceIdAndMemberId(
                        WORKSPACE_ID,
                        MEMBER_ID
                )
        ).thenReturn(false);
        ThrowingCallable action = () -> service.findTree(
                WORKSPACE_ID,
                MEMBER_ID
        );

        // when
        Throwable thrown = org.assertj.core.api.Assertions.catchThrowable(action);

        // then
        assertThat(thrown).isInstanceOf(WorkspaceException.class)
                .extracting(exception -> ((WorkspaceException) exception).getErrorCode())
                .isEqualTo(WorkspaceErrorCode.WORKSPACE_ACCESS_DENIED);
        verifyNoInteractions(importedPageRepository);
    }

    @DisplayName("부모 누락, 자기 참조, 순환 참조가 있으면 전체 Tree 조회를 실패시킨다")
    @MethodSource("invalidTreeCases")
    @ParameterizedTest(name = "{0}")
    void findTree_failure_invalidHierarchy(
            String caseName,
            List<PageReference> pageReferences
    ) {
        // given
        allowWorkspaceMember();
        List<ImportedPageMetadata> importedPages = pageReferences.stream()
                .map(
                        reference -> importedPage(
                                reference.id(),
                                reference.parentId(),
                                "Page " + reference.id(),
                                0,
                                "https://content.example/pages/" + reference.id()
                        )
                )
                .toList();
        when(importedPageRepository.findPublishedMetadataByWorkspaceIdOrderByPositionAscIdAsc(WORKSPACE_ID))
                .thenReturn(importedPages);
        ThrowingCallable action = () -> service.findTree(
                WORKSPACE_ID,
                MEMBER_ID
        );

        // when
        Throwable thrown = org.assertj.core.api.Assertions.catchThrowable(action);

        // then
        assertThat(thrown).isInstanceOf(ImportedPageException.class)
                .extracting(exception -> ((ImportedPageException) exception).getErrorCode())
                .isEqualTo(ImportedPageErrorCode.IMPORTED_PAGE_TREE_INVALID);
    }

    private void allowWorkspaceMember() {
        when(workspaceRepository.findById(WORKSPACE_ID)).thenReturn(Optional.of(mock(Workspace.class)));
        when(
                workspaceMemberRepository.existsByWorkspaceIdAndMemberId(
                        WORKSPACE_ID,
                        MEMBER_ID
                )
        ).thenReturn(true);
    }

    private ImportedPageMetadata importedPage(
            Long id,
            Long parentId,
            String title,
            int position,
            String sourceUrl
    ) {
        return new ImportedPageMetadata(
                id,
                WORKSPACE_ID,
                parentId,
                title,
                position,
                sourceUrl
        );
    }

    private static Stream<Arguments> invalidTreeCases() {
        return Stream.of(
                Arguments.of(
                        "부모 누락",
                        List.of(
                                new PageReference(
                                        1L,
                                        2L
                                )
                        )
                ),
                Arguments.of(
                        "자기 참조",
                        List.of(
                                new PageReference(
                                        1L,
                                        1L
                                )
                        )
                ),
                Arguments.of(
                        "순환 참조",
                        List.of(
                                new PageReference(
                                        1L,
                                        2L
                                ),
                                new PageReference(
                                        2L,
                                        1L
                                )
                        )
                )
        );
    }

    private record PageReference(
            Long id,
            Long parentId
    ) {
    }
}
