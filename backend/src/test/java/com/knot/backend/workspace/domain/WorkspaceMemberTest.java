package com.knot.backend.workspace.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class WorkspaceMemberTest {
    private static final Instant JOINED_AT = Instant.parse("2026-08-24T00:00:00Z");

    @DisplayName("승계가 확인되지 않은 대상이 있으면 OWNER 탈퇴를 거부한다")
    @ParameterizedTest
    @ValueSource(strings = {"missing", "self", "otherWorkspace", "member", "leftOwner"})
    void leaveAfterOwnershipTransfer_failure_invalidSuccessor(String scenario) {
        // given
        WorkspaceMember owner = WorkspaceMember.create(
                1L,
                2L,
                WorkspaceMemberRole.OWNER,
                JOINED_AT
        );
        owner.markLastViewed();
        WorkspaceMember successor = invalidSuccessor(scenario);

        // when
        ThrowingCallable action = () -> owner.leaveAfterOwnershipTransfer(
                successor,
                JOINED_AT.plusSeconds(1)
        );

        // then
        assertThatThrownBy(action).isInstanceOf(WorkspaceException.class)
                .extracting(exception -> ((WorkspaceException) exception).getErrorCode())
                .isEqualTo(WorkspaceErrorCode.WORKSPACE_OWNERSHIP_TRANSFER_TARGET_CONFLICT);
        assertThat(owner.isActive()).isTrue();
        assertThat(owner.isLastViewed()).isTrue();
        assertThat(owner.getRole()).isEqualTo(WorkspaceMemberRole.OWNER);
    }

    @DisplayName("워크스페이스와 멤버 식별자 및 역할로 멤버십을 생성한다")
    @Test
    void create_success() {
        // given
        Long workspaceId = 1L;
        Long memberId = 2L;

        // when
        WorkspaceMember workspaceMember = WorkspaceMember.create(
                workspaceId,
                memberId,
                WorkspaceMemberRole.OWNER,
                JOINED_AT
        );

        // then
        assertThat(workspaceMember.getWorkspaceId()).isEqualTo(workspaceId);
        assertThat(workspaceMember.getMemberId()).isEqualTo(memberId);
        assertThat(workspaceMember.getRole()).isEqualTo(WorkspaceMemberRole.OWNER);
        assertThat(workspaceMember.getJoinedAt()).isEqualTo(JOINED_AT);
        assertThat(workspaceMember.isLastViewed()).isFalse();
    }

    @DisplayName("마지막으로 본 워크스페이스 멤버십으로 표시한다")
    @Test
    void markLastViewed_success() {
        // given
        WorkspaceMember workspaceMember = WorkspaceMember.create(
                1L,
                2L,
                WorkspaceMemberRole.OWNER,
                JOINED_AT
        );

        // when
        workspaceMember.markLastViewed();

        // then
        assertThat(workspaceMember.isLastViewed()).isTrue();
    }

    @DisplayName("마지막으로 본 워크스페이스 멤버십 표시를 해제한다")
    @Test
    void clearLastViewed_success() {
        // given
        WorkspaceMember workspaceMember = WorkspaceMember.create(
                1L,
                2L,
                WorkspaceMemberRole.OWNER,
                JOINED_AT
        );
        workspaceMember.markLastViewed();

        // when
        workspaceMember.clearLastViewed();

        // then
        assertThat(workspaceMember.isLastViewed()).isFalse();
    }

    @DisplayName("활성 MEMBER가 OWNER 권한을 승계하면 역할만 OWNER로 바뀐다")
    @Test
    void receiveOwnership_success() {
        // given
        WorkspaceMember workspaceMember = WorkspaceMember.create(
                1L,
                2L,
                WorkspaceMemberRole.MEMBER,
                JOINED_AT
        );
        workspaceMember.markLastViewed();

        // when
        workspaceMember.receiveOwnership();

        // then
        assertThat(workspaceMember.getRole()).isEqualTo(WorkspaceMemberRole.OWNER);
        assertThat(workspaceMember.getJoinedAt()).isEqualTo(JOINED_AT);
        assertThat(workspaceMember.isLastViewed()).isTrue();
        assertThat(workspaceMember.getLeftAt()).isNull();
        assertThat(workspaceMember.isActive()).isTrue();
    }

    @DisplayName("탈퇴한 MEMBER는 OWNER 권한을 승계할 수 없다")
    @Test
    void receiveOwnership_failure_leftMember() {
        // given
        WorkspaceMember workspaceMember = WorkspaceMember.create(
                1L,
                2L,
                WorkspaceMemberRole.MEMBER,
                JOINED_AT
        );
        Instant leftAt = JOINED_AT.plusSeconds(1);
        workspaceMember.leave(
                leftAt,
                2L
        );

        // when
        ThrowingCallable action = workspaceMember::receiveOwnership;

        // then
        assertThatThrownBy(action).isInstanceOf(WorkspaceException.class)
                .extracting(exception -> ((WorkspaceException) exception).getErrorCode())
                .isEqualTo(WorkspaceErrorCode.WORKSPACE_OWNERSHIP_TRANSFER_TARGET_CONFLICT);
        assertThat(workspaceMember.getRole()).isEqualTo(WorkspaceMemberRole.MEMBER);
        assertThat(workspaceMember.getLeftAt()).isEqualTo(leftAt);
    }

    @DisplayName("이미 OWNER인 멤버십은 OWNER 권한을 다시 승계할 수 없다")
    @Test
    void receiveOwnership_failure_owner() {
        // given
        WorkspaceMember workspaceMember = WorkspaceMember.create(
                1L,
                2L,
                WorkspaceMemberRole.OWNER,
                JOINED_AT
        );

        // when
        ThrowingCallable action = workspaceMember::receiveOwnership;

        // then
        assertThatThrownBy(action).isInstanceOf(WorkspaceException.class)
                .extracting(exception -> ((WorkspaceException) exception).getErrorCode())
                .isEqualTo(WorkspaceErrorCode.WORKSPACE_OWNERSHIP_TRANSFER_TARGET_CONFLICT);
        assertThat(workspaceMember.getRole()).isEqualTo(WorkspaceMemberRole.OWNER);
        assertThat(workspaceMember.getLeftAt()).isNull();
    }

    @DisplayName("탈퇴한 OWNER는 OWNER 권한을 승계할 수 없다")
    @Test
    void receiveOwnership_failure_leftOwner() {
        // given
        WorkspaceMember workspaceMember = WorkspaceMember.create(
                1L,
                2L,
                WorkspaceMemberRole.OWNER,
                JOINED_AT
        );
        Instant leftAt = JOINED_AT.plusSeconds(1);
        workspaceMember.leave(
                leftAt,
                1L
        );

        // when
        ThrowingCallable action = workspaceMember::receiveOwnership;

        // then
        assertThatThrownBy(action).isInstanceOf(WorkspaceException.class)
                .extracting(exception -> ((WorkspaceException) exception).getErrorCode())
                .isEqualTo(WorkspaceErrorCode.WORKSPACE_OWNERSHIP_TRANSFER_TARGET_CONFLICT);
        assertThat(workspaceMember.getRole()).isEqualTo(WorkspaceMemberRole.OWNER);
        assertThat(workspaceMember.getLeftAt()).isEqualTo(leftAt);
    }

    @DisplayName("멤버십을 탈퇴 상태로 바꾸면 마지막 조회 상태도 해제한다")
    @Test
    void leave_success() {
        // given
        WorkspaceMember workspaceMember = WorkspaceMember.create(
                1L,
                2L,
                WorkspaceMemberRole.MEMBER,
                JOINED_AT
        );
        workspaceMember.markLastViewed();
        Instant leftAt = JOINED_AT.plusSeconds(1);

        // when
        workspaceMember.leave(
                leftAt,
                2L
        );

        // then
        assertThat(workspaceMember.isActive()).isFalse();
        assertThat(workspaceMember.getLeftAt()).isEqualTo(leftAt);
        assertThat(workspaceMember.isLastViewed()).isFalse();
    }

    @DisplayName("승계 후 OWNER 탈퇴는 역할과 참여 시각을 보존하고 마지막 조회 상태를 해제한다")
    @Test
    void leaveAfterOwnershipTransfer_success() {
        // given
        WorkspaceMember workspaceMember = WorkspaceMember.create(
                1L,
                2L,
                WorkspaceMemberRole.MEMBER,
                JOINED_AT
        );
        workspaceMember.markLastViewed();
        workspaceMember.receiveOwnership();
        Instant leftAt = JOINED_AT.plusSeconds(1);

        // when
        workspaceMember.leaveAfterOwnershipTransfer(
                ownershipSuccessor(),
                leftAt
        );

        // then
        assertThat(workspaceMember.getRole()).isEqualTo(WorkspaceMemberRole.OWNER);
        assertThat(workspaceMember.getJoinedAt()).isEqualTo(JOINED_AT);
        assertThat(workspaceMember.getLeftAt()).isEqualTo(leftAt);
        assertThat(workspaceMember.isLastViewed()).isFalse();
        assertThat(workspaceMember.isActive()).isFalse();
    }

    @DisplayName("활성 MEMBER는 승계 후 탈퇴를 수행할 수 없다")
    @Test
    void leaveAfterOwnershipTransfer_failure_member() {
        // given
        WorkspaceMember workspaceMember = WorkspaceMember.create(
                1L,
                2L,
                WorkspaceMemberRole.MEMBER,
                JOINED_AT
        );
        workspaceMember.markLastViewed();
        Instant leftAt = JOINED_AT.plusSeconds(1);

        // when
        ThrowingCallable action = () -> workspaceMember.leaveAfterOwnershipTransfer(
                ownershipSuccessor(),
                leftAt
        );

        // then
        assertThatThrownBy(action).isInstanceOf(WorkspaceException.class)
                .extracting(exception -> ((WorkspaceException) exception).getErrorCode())
                .isEqualTo(WorkspaceErrorCode.WORKSPACE_OWNER_REQUIRED);
        assertThat(workspaceMember.getRole()).isEqualTo(WorkspaceMemberRole.MEMBER);
        assertThat(workspaceMember.getLeftAt()).isNull();
        assertThat(workspaceMember.isLastViewed()).isTrue();
    }

    @DisplayName("탈퇴한 MEMBER는 승계 후 탈퇴를 수행할 수 없다")
    @Test
    void leaveAfterOwnershipTransfer_failure_leftMember() {
        // given
        WorkspaceMember workspaceMember = WorkspaceMember.create(
                1L,
                2L,
                WorkspaceMemberRole.MEMBER,
                JOINED_AT
        );
        Instant firstLeftAt = JOINED_AT.plusSeconds(1);
        workspaceMember.leave(
                firstLeftAt,
                2L
        );
        Instant secondLeftAt = JOINED_AT.plusSeconds(2);

        // when
        ThrowingCallable action = () -> workspaceMember.leaveAfterOwnershipTransfer(
                ownershipSuccessor(),
                secondLeftAt
        );

        // then
        assertThatThrownBy(action).isInstanceOf(WorkspaceException.class)
                .extracting(exception -> ((WorkspaceException) exception).getErrorCode())
                .isEqualTo(WorkspaceErrorCode.WORKSPACE_OWNER_REQUIRED);
        assertThat(workspaceMember.getRole()).isEqualTo(WorkspaceMemberRole.MEMBER);
        assertThat(workspaceMember.getLeftAt()).isEqualTo(firstLeftAt);
    }

    @DisplayName("탈퇴한 OWNER는 승계 후 탈퇴를 수행할 수 없다")
    @Test
    void leaveAfterOwnershipTransfer_failure_leftOwner() {
        // given
        WorkspaceMember workspaceMember = WorkspaceMember.create(
                1L,
                2L,
                WorkspaceMemberRole.OWNER,
                JOINED_AT
        );
        Instant firstLeftAt = JOINED_AT.plusSeconds(1);
        workspaceMember.leave(
                firstLeftAt,
                1L
        );
        Instant secondLeftAt = JOINED_AT.plusSeconds(2);

        // when
        ThrowingCallable action = () -> workspaceMember.leaveAfterOwnershipTransfer(
                ownershipSuccessor(),
                secondLeftAt
        );

        // then
        assertThatThrownBy(action).isInstanceOf(WorkspaceException.class)
                .extracting(exception -> ((WorkspaceException) exception).getErrorCode())
                .isEqualTo(WorkspaceErrorCode.WORKSPACE_OWNER_REQUIRED);
        assertThat(workspaceMember.getRole()).isEqualTo(WorkspaceMemberRole.OWNER);
        assertThat(workspaceMember.getLeftAt()).isEqualTo(firstLeftAt);
    }

    @DisplayName("승계 후 탈퇴 시각이 참여 시각보다 빠르면 상태를 변경하지 않는다")
    @Test
    void leaveAfterOwnershipTransfer_failure_leftAtBeforeJoinedAt() {
        // given
        WorkspaceMember workspaceMember = WorkspaceMember.create(
                1L,
                2L,
                WorkspaceMemberRole.MEMBER,
                JOINED_AT
        );
        workspaceMember.markLastViewed();
        workspaceMember.receiveOwnership();
        Instant leftAtBeforeJoinedAt = JOINED_AT.minusSeconds(1);

        // when
        ThrowingCallable action = () -> workspaceMember.leaveAfterOwnershipTransfer(
                ownershipSuccessor(),
                leftAtBeforeJoinedAt
        );

        // then
        assertThatThrownBy(action).isInstanceOf(WorkspaceException.class)
                .extracting(exception -> ((WorkspaceException) exception).getErrorCode())
                .isEqualTo(WorkspaceErrorCode.INVALID_WORKSPACE_MEMBER_LEFT_AT);
        assertThat(workspaceMember.getRole()).isEqualTo(WorkspaceMemberRole.OWNER);
        assertThat(workspaceMember.getLeftAt()).isNull();
        assertThat(workspaceMember.isLastViewed()).isTrue();
        assertThat(workspaceMember.isActive()).isTrue();
    }

    @DisplayName("이미 탈퇴한 멤버십에 다시 탈퇴를 요청하면 기존 탈퇴 시각을 유지한다")
    @Test
    void leave_success_alreadyLeft() {
        // given
        WorkspaceMember workspaceMember = WorkspaceMember.create(
                1L,
                2L,
                WorkspaceMemberRole.MEMBER,
                JOINED_AT
        );
        Instant firstLeftAt = JOINED_AT.plusSeconds(1);
        Instant secondLeftAt = JOINED_AT.plusSeconds(2);
        workspaceMember.leave(
                firstLeftAt,
                2L
        );

        // when
        workspaceMember.leave(
                secondLeftAt,
                2L
        );

        // then
        assertThat(workspaceMember.getLeftAt()).isEqualTo(firstLeftAt);
    }

    @DisplayName("다른 활성 멤버가 있는 OWNER는 일반 탈퇴할 수 없다")
    @Test
    void leave_failure_ownerWithOtherActiveMember() {
        // given
        WorkspaceMember workspaceMember = WorkspaceMember.create(
                1L,
                2L,
                WorkspaceMemberRole.OWNER,
                JOINED_AT
        );
        Instant leftAt = JOINED_AT.plusSeconds(1);

        // when
        ThrowingCallable action = () -> workspaceMember.leave(
                leftAt,
                2L
        );

        // then
        assertThatThrownBy(action).isInstanceOf(WorkspaceException.class)
                .extracting(exception -> ((WorkspaceException) exception).getErrorCode())
                .isEqualTo(WorkspaceErrorCode.WORKSPACE_OWNER_TRANSFER_REQUIRED);
    }

    @DisplayName("탈퇴 시각이 참여 시각보다 빠르면 멤버십 탈퇴를 거부한다")
    @Test
    void leave_failure_leftAtBeforeJoinedAt() {
        // given
        WorkspaceMember workspaceMember = WorkspaceMember.create(
                1L,
                2L,
                WorkspaceMemberRole.MEMBER,
                JOINED_AT
        );
        Instant leftAtBeforeJoinedAt = JOINED_AT.minusSeconds(1);

        // when
        ThrowingCallable action = () -> workspaceMember.leave(
                leftAtBeforeJoinedAt,
                1L
        );

        // then
        assertThatThrownBy(action).isInstanceOf(WorkspaceException.class)
                .extracting(exception -> ((WorkspaceException) exception).getErrorCode())
                .isEqualTo(WorkspaceErrorCode.INVALID_WORKSPACE_MEMBER_LEFT_AT);
    }

    @DisplayName("활성 멤버 수가 양수가 아니면 멤버십 탈퇴를 거부한다")
    @Test
    void leave_failure_invalidActiveMemberCount() {
        // given
        WorkspaceMember workspaceMember = WorkspaceMember.create(
                1L,
                2L,
                WorkspaceMemberRole.MEMBER,
                JOINED_AT
        );
        Instant leftAt = JOINED_AT.plusSeconds(1);

        // when
        ThrowingCallable action = () -> workspaceMember.leave(
                leftAt,
                0L
        );

        // then
        assertThatThrownBy(action).isInstanceOf(WorkspaceException.class)
                .extracting(exception -> ((WorkspaceException) exception).getErrorCode())
                .isEqualTo(WorkspaceErrorCode.INVALID_WORKSPACE_MEMBER_ACTIVE_COUNT);
    }

    @DisplayName("탈퇴한 멤버십은 마지막 조회 상태로 표시할 수 없다")
    @Test
    void markLastViewed_failure_leftMembership() {
        // given
        WorkspaceMember workspaceMember = WorkspaceMember.create(
                1L,
                2L,
                WorkspaceMemberRole.MEMBER,
                JOINED_AT
        );
        workspaceMember.leave(
                JOINED_AT.plusSeconds(1),
                2L
        );

        // when
        ThrowingCallable action = workspaceMember::markLastViewed;

        // then
        assertThatThrownBy(action).isInstanceOf(WorkspaceException.class)
                .extracting(exception -> ((WorkspaceException) exception).getErrorCode())
                .isEqualTo(WorkspaceErrorCode.WORKSPACE_MEMBER_ALREADY_LEFT);
    }

    @DisplayName("워크스페이스 ID가 양수가 아니면 멤버십 생성을 거부한다")
    @Test
    void create_failure_invalidWorkspaceId() {
        // given
        Long invalidWorkspaceId = 0L;

        // when
        ThrowingCallable action = () -> WorkspaceMember.create(
                invalidWorkspaceId,
                1L,
                WorkspaceMemberRole.MEMBER,
                JOINED_AT
        );

        // then
        assertThatThrownBy(action).isInstanceOf(WorkspaceException.class)
                .extracting(exception -> ((WorkspaceException) exception).getErrorCode())
                .isEqualTo(WorkspaceErrorCode.INVALID_WORKSPACE_ID);
    }

    @DisplayName("멤버 ID가 양수가 아니면 멤버십 생성을 거부한다")
    @Test
    void create_failure_invalidMemberId() {
        // given
        Long invalidMemberId = 0L;

        // when
        ThrowingCallable action = () -> WorkspaceMember.create(
                1L,
                invalidMemberId,
                WorkspaceMemberRole.MEMBER,
                JOINED_AT
        );

        // then
        assertThatThrownBy(action).isInstanceOf(WorkspaceException.class)
                .extracting(exception -> ((WorkspaceException) exception).getErrorCode())
                .isEqualTo(WorkspaceErrorCode.INVALID_MEMBER_ID);
    }

    @DisplayName("역할이 없으면 멤버십 생성을 거부한다")
    @Test
    void create_failure_missingRole() {
        // given
        WorkspaceMemberRole missingRole = null;

        // when
        ThrowingCallable action = () -> WorkspaceMember.create(
                1L,
                1L,
                missingRole,
                JOINED_AT
        );

        // then
        assertThatThrownBy(action).isInstanceOf(WorkspaceException.class)
                .extracting(exception -> ((WorkspaceException) exception).getErrorCode())
                .isEqualTo(WorkspaceErrorCode.INVALID_WORKSPACE_MEMBER_ROLE);
    }

    @DisplayName("참여 시각이 없으면 멤버십 생성을 거부한다")
    @Test
    void create_failure_missingJoinedAt() {
        // given
        Instant missingJoinedAt = null;

        // when
        ThrowingCallable action = () -> WorkspaceMember.create(
                1L,
                1L,
                WorkspaceMemberRole.MEMBER,
                missingJoinedAt
        );

        // then
        assertThatThrownBy(action).isInstanceOf(WorkspaceException.class)
                .extracting(exception -> ((WorkspaceException) exception).getErrorCode())
                .isEqualTo(WorkspaceErrorCode.INVALID_WORKSPACE_MEMBER_JOINED_AT);
    }
    private WorkspaceMember ownershipSuccessor() {
        return WorkspaceMember.create(
                1L,
                3L,
                WorkspaceMemberRole.OWNER,
                JOINED_AT
        );
    }

    private WorkspaceMember invalidSuccessor(String scenario) {
        if (scenario.equals("missing")) {
            return null;
        }
        WorkspaceMember successor = WorkspaceMember.create(
                scenario.equals("otherWorkspace") ? 2L : 1L,
                scenario.equals("self") ? 2L : 3L,
                scenario.equals("member") ? WorkspaceMemberRole.MEMBER : WorkspaceMemberRole.OWNER,
                JOINED_AT
        );
        if (scenario.equals("leftOwner")) {
            successor.leave(
                    JOINED_AT.plusSeconds(1),
                    1L
            );
        }
        return successor;
    }
}
