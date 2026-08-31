package com.knot.backend.workspace.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.knot.backend.workspace.application.dto.result.NotionPageTreeItemResult;
import com.knot.backend.workspace.domain.NotionErrorCode;
import com.knot.backend.workspace.domain.NotionException;
import com.knot.backend.workspace.domain.NotionPageMetadata;
import com.knot.backend.workspace.domain.NotionPageRepository;
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

class NotionPageTreeQueryServiceTest {
    private static final Long WORKSPACE_ID = 1L;
    private static final long MEMBER_ID = 10L;

    private final WorkspaceRepository workspaceRepository = mock(WorkspaceRepository.class);
    private final WorkspaceMemberRepository workspaceMemberRepository = mock(WorkspaceMemberRepository.class);
    private final NotionPageRepository notionPageRepository = mock(NotionPageRepository.class);
    private final NotionPageTreeQueryService service = new NotionPageTreeQueryService(
            workspaceRepository,
            workspaceMemberRepository,
            notionPageRepository
    );

    @DisplayName("Workspace 멤버에게 발행된 Page를 평면 Tree 계약으로 변환한다")
    @Test
    void findTree_success_mapsPublishedPages() {
        // given
        allowWorkspaceMember();
        NotionPageMetadata rootPage = notionPage(
                1L,
                null,
                "루트",
                0,
                "https://www.notion.so/root"
        );
        NotionPageMetadata childPage = notionPage(
                2L,
                1L,
                "자식",
                1,
                "https://www.notion.so/child"
        );
        when(notionPageRepository.findPublishedMetadataByWorkspaceIdOrderByPositionAscIdAsc(WORKSPACE_ID)).thenReturn(
                List.of(
                        rootPage,
                        childPage
                )
        );

        // when
        List<NotionPageTreeItemResult> result = service.findTree(
                WORKSPACE_ID,
                MEMBER_ID
        );

        // then
        assertThat(result).extracting(
                NotionPageTreeItemResult::id,
                NotionPageTreeItemResult::parentPageId,
                NotionPageTreeItemResult::title,
                NotionPageTreeItemResult::position,
                NotionPageTreeItemResult::notionUrl
        )
                .containsExactly(
                        tuple(
                                1L,
                                null,
                                "루트",
                                0,
                                "https://www.notion.so/root"
                        ),
                        tuple(
                                2L,
                                1L,
                                "자식",
                                1,
                                "https://www.notion.so/child"
                        )
                );
        verify(notionPageRepository).findPublishedMetadataByWorkspaceIdOrderByPositionAscIdAsc(WORKSPACE_ID);
    }

    @DisplayName("발행된 Page가 없으면 빈 배열을 반환한다")
    @Test
    void findTree_success_emptyPublishedPages() {
        // given
        allowWorkspaceMember();
        when(notionPageRepository.findPublishedMetadataByWorkspaceIdOrderByPositionAscIdAsc(WORKSPACE_ID))
                .thenReturn(List.of());

        // when
        List<NotionPageTreeItemResult> result = service.findTree(
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
                notionPageRepository
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
                notionPageRepository
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
        verifyNoInteractions(notionPageRepository);
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
        List<NotionPageMetadata> notionPages = pageReferences.stream()
                .map(
                        reference -> notionPage(
                                reference.id(),
                                reference.parentPageId(),
                                "Page " + reference.id(),
                                0,
                                "https://www.notion.so/" + reference.id()
                        )
                )
                .toList();
        when(notionPageRepository.findPublishedMetadataByWorkspaceIdOrderByPositionAscIdAsc(WORKSPACE_ID))
                .thenReturn(notionPages);
        ThrowingCallable action = () -> service.findTree(
                WORKSPACE_ID,
                MEMBER_ID
        );

        // when
        Throwable thrown = org.assertj.core.api.Assertions.catchThrowable(action);

        // then
        assertThat(thrown).isInstanceOf(NotionException.class)
                .extracting(exception -> ((NotionException) exception).getErrorCode())
                .isEqualTo(NotionErrorCode.NOTION_PAGE_TREE_INVALID);
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

    private NotionPageMetadata notionPage(
            Long id,
            Long parentPageId,
            String title,
            int position,
            String notionUrl
    ) {
        return new NotionPageMetadata(
                id,
                WORKSPACE_ID,
                parentPageId,
                title,
                position,
                notionUrl
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
            Long parentPageId
    ) {
    }
}
