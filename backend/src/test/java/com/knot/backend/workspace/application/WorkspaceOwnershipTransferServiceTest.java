package com.knot.backend.workspace.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.knot.backend.document.application.DocumentArchivalService;
import com.knot.backend.recording.domain.RecordingSessionRepository;
import com.knot.backend.workspace.domain.Workspace;
import com.knot.backend.workspace.domain.WorkspaceErrorCode;
import com.knot.backend.workspace.domain.WorkspaceException;
import com.knot.backend.workspace.domain.WorkspaceMember;
import com.knot.backend.workspace.domain.WorkspaceMemberRepository;
import com.knot.backend.workspace.domain.WorkspaceMemberRole;
import com.knot.backend.workspace.domain.WorkspaceRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;

class WorkspaceOwnershipTransferServiceTest {

    private static final Instant JOINED_AT = Instant.parse("2026-09-30T00:00:00Z");
    private static final Instant NOW = Instant.parse("2026-09-30T00:01:00.123456789Z");
    private static final Instant LEFT_AT = Instant.parse("2026-09-30T00:01:00.123456Z");

    private final WorkspaceRepository workspaceRepository = mock(WorkspaceRepository.class);
    private final WorkspaceMemberRepository workspaceMemberRepository = mock(WorkspaceMemberRepository.class);
    private final RecordingSessionRepository recordingSessionRepository = mock(RecordingSessionRepository.class);
    private final DocumentArchivalService documentArchivalService = mock(DocumentArchivalService.class);
    private final WorkspaceOwnershipTransferService service = new WorkspaceOwnershipTransferService(
            workspaceRepository,
            workspaceMemberRepository,
            recordingSessionRepository,
            Clock.fixed(
                    NOW,
                    ZoneOffset.UTC
            ),
            documentArchivalService
    );

    @DisplayName("Workspace를 먼저 잠그고 승계와 탈퇴를 조정한다")
    @Test
    void transferOwnership_success() {
        // given
        WorkspaceMember owner = participant(
                2L,
                WorkspaceMemberRole.OWNER
        );
        WorkspaceMember successor = participant(
                3L,
                WorkspaceMemberRole.MEMBER
        );
        owner.markLastViewed();
        successor.markLastViewed();
        stubParticipants(
                List.of(
                        successor,
                        owner
                )
        );

        // when
        service.transferOwnership(
                2L,
                1L,
                3L
        );

        // then
        assertThat(owner.getLeftAt()).isEqualTo(Instant.parse("2026-09-30T00:01:00.123456Z"));
        assertThat(owner.isLastViewed()).isFalse();
        assertThat(owner.getRole()).isEqualTo(WorkspaceMemberRole.OWNER);
        assertThat(successor.getRole()).isEqualTo(WorkspaceMemberRole.OWNER);
        assertThat(successor.isActive()).isTrue();
        assertThat(successor.isLastViewed()).isTrue();
        InOrder order = inOrder(
                workspaceRepository,
                workspaceMemberRepository,
                recordingSessionRepository,
                documentArchivalService
        );
        order.verify(workspaceRepository)
                .findByIdForUpdate(1L);
        order.verify(workspaceMemberRepository)
                .findAllActiveByWorkspaceIdAndMemberIdsForUpdate(
                        1L,
                        List.of(
                                2L,
                                3L
                        )
                );
        order.verify(workspaceMemberRepository)
                .saveAll(
                        List.of(
                                owner,
                                successor
                        )
                );
        order.verify(recordingSessionRepository)
                .findAllActiveByWorkspaceIdAndMemberIdForUpdate(
                        1L,
                        2L
                );
        order.verify(workspaceMemberRepository)
                .flush();
        order.verify(documentArchivalService)
                .archiveAfterMemberDeparture(
                        1L,
                        2L,
                        LEFT_AT
                );
    }

    @DisplayName("보관 실패를 전파해 소유권 승계와 OWNER 탈퇴가 함께 롤백되도록 한다")
    @Test
    void transferOwnership_failure_documentArchivalFailed() {
        // given
        WorkspaceMember owner = participant(
                2L,
                WorkspaceMemberRole.OWNER
        );
        WorkspaceMember successor = participant(
                3L,
                WorkspaceMemberRole.MEMBER
        );
        stubParticipants(
                List.of(
                        owner,
                        successor
                )
        );
        IllegalStateException failure = new IllegalStateException("문서 보관 실패");
        doThrow(failure).when(documentArchivalService)
                .archiveAfterMemberDeparture(
                        1L,
                        2L,
                        LEFT_AT
                );

        // when
        ThrowingCallable action = () -> service.transferOwnership(
                2L,
                1L,
                3L
        );

        // then
        assertThatThrownBy(action).isSameAs(failure);
        InOrder order = inOrder(
                workspaceMemberRepository,
                documentArchivalService
        );
        order.verify(workspaceMemberRepository)
                .flush();
        order.verify(documentArchivalService)
                .archiveAfterMemberDeparture(
                        1L,
                        2L,
                        LEFT_AT
                );
    }

    @DisplayName("본인 지정은 저장소 호출 없이 400으로 거절한다")
    @Test
    void transferOwnership_failure_self() {
        // given
        long memberId = 2L;

        // when
        ThrowingCallable action = () -> service.transferOwnership(
                memberId,
                1L,
                memberId
        );

        // then
        assertError(
                action,
                WorkspaceErrorCode.INVALID_WORKSPACE_OWNERSHIP_TRANSFER_TARGET
        );
        verifyNoInteractions(
                workspaceRepository,
                workspaceMemberRepository,
                documentArchivalService
        );
    }

    @DisplayName("잘못된 Workspace 식별자는 조회하지 않는다")
    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {0L, -1L})
    void transferOwnership_failure_invalidWorkspaceId(Long workspaceId) {
        // given
        long memberId = 2L;

        // when
        ThrowingCallable action = () -> service.transferOwnership(
                memberId,
                workspaceId,
                3L
        );

        // then
        assertError(
                action,
                WorkspaceErrorCode.INVALID_WORKSPACE_ID
        );
        verifyNoInteractions(
                workspaceRepository,
                workspaceMemberRepository
        );
    }

    @DisplayName("미존재 또는 삭제된 Workspace는 참여 행을 잠그지 않는다")
    @Test
    void transferOwnership_failure_workspaceNotFound() {
        // given
        when(workspaceRepository.findByIdForUpdate(1L)).thenReturn(Optional.empty());

        // when
        ThrowingCallable action = () -> service.transferOwnership(
                2L,
                1L,
                3L
        );

        // then
        assertError(
                action,
                WorkspaceErrorCode.WORKSPACE_NOT_FOUND
        );
        verifyNoInteractions(workspaceMemberRepository);
    }

    @DisplayName("활성 OWNER가 아닌 요청자는 대상 상태를 변경하지 않는다")
    @ParameterizedTest
    @ValueSource(strings = {"missing", "member"})
    void transferOwnership_failure_ownerRequired(String actorState) {
        // given
        WorkspaceMember successor = participant(
                3L,
                WorkspaceMemberRole.MEMBER
        );
        List<WorkspaceMember> participants = participantsWithoutActiveOwner(
                actorState,
                successor
        );
        stubParticipants(participants);

        // when
        ThrowingCallable action = () -> service.transferOwnership(
                2L,
                1L,
                3L
        );

        // then
        assertError(
                action,
                WorkspaceErrorCode.WORKSPACE_OWNER_REQUIRED
        );
        assertThat(successor.getRole()).isEqualTo(WorkspaceMemberRole.MEMBER);
        verify(
                workspaceMemberRepository,
                never()
        ).saveAll(any());
    }

    @DisplayName("활성 MEMBER 대상이 없으면 기존 OWNER 상태를 유지한다")
    @ParameterizedTest
    @ValueSource(strings = {"missing", "owner"})
    void transferOwnership_failure_targetConflict(String targetState) {
        // given
        WorkspaceMember owner = participant(
                2L,
                WorkspaceMemberRole.OWNER
        );
        owner.markLastViewed();
        List<WorkspaceMember> participants = participantsWithoutEligibleSuccessor(
                targetState,
                owner
        );
        stubParticipants(participants);

        // when
        ThrowingCallable action = () -> service.transferOwnership(
                2L,
                1L,
                3L
        );

        // then
        assertError(
                action,
                WorkspaceErrorCode.WORKSPACE_OWNERSHIP_TRANSFER_TARGET_CONFLICT
        );
        assertThat(owner.isActive()).isTrue();
        assertThat(owner.isLastViewed()).isTrue();
        verify(
                workspaceMemberRepository,
                never()
        ).saveAll(any());
    }

    private List<WorkspaceMember> participantsWithoutActiveOwner(
            String actorState,
            WorkspaceMember successor
    ) {
        if (actorState.equals("missing")) {
            return List.of(successor);
        }
        return List.of(
                participant(
                        2L,
                        WorkspaceMemberRole.MEMBER
                ),
                successor
        );
    }

    private List<WorkspaceMember> participantsWithoutEligibleSuccessor(
            String targetState,
            WorkspaceMember owner
    ) {
        if (targetState.equals("missing")) {
            return List.of(owner);
        }
        return List.of(
                owner,
                participant(
                        3L,
                        WorkspaceMemberRole.OWNER
                )
        );
    }

    private WorkspaceMember participant(
            long memberId,
            WorkspaceMemberRole role
    ) {
        return WorkspaceMember.create(
                1L,
                memberId,
                role,
                JOINED_AT
        );
    }

    private void stubParticipants(List<WorkspaceMember> participants) {
        when(workspaceRepository.findByIdForUpdate(1L)).thenReturn(
                Optional.of(
                        Workspace.create(
                                "팀",
                                JOINED_AT
                        )
                )
        );
        when(
                workspaceMemberRepository.findAllActiveByWorkspaceIdAndMemberIdsForUpdate(
                        1L,
                        List.of(
                                2L,
                                3L
                        )
                )
        ).thenReturn(participants);
    }

    private void assertError(
            ThrowingCallable action,
            WorkspaceErrorCode errorCode
    ) {
        assertThatThrownBy(action).isInstanceOf(WorkspaceException.class)
                .extracting(exception -> ((WorkspaceException) exception).getErrorCode())
                .isEqualTo(errorCode);
    }
}
