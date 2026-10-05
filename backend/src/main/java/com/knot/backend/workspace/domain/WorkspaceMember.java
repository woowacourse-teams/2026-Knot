package com.knot.backend.workspace.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;

@Getter
@Entity
@Table(name = "workspace_members")
public class WorkspaceMember {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "workspace_id", nullable = false)
    private Long workspaceId;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WorkspaceMemberRole role;

    @Column(name = "joined_at", nullable = false, updatable = false)
    private Instant joinedAt;

    @Column(name = "last_viewed", nullable = false)
    private boolean lastViewed;

    @Column(name = "left_at")
    private Instant leftAt;

    protected WorkspaceMember() {}

    private WorkspaceMember(
            Long workspaceId,
            Long memberId,
            WorkspaceMemberRole role,
            Instant joinedAt
    ) {
        validateWorkspaceId(workspaceId);
        validateMemberId(memberId);
        validateRole(role);
        validateJoinedAt(joinedAt);
        this.workspaceId = workspaceId;
        this.memberId = memberId;
        this.role = role;
        this.joinedAt = joinedAt;
        this.lastViewed = false;
        this.leftAt = null;
    }

    public static WorkspaceMember create(
            Long workspaceId,
            Long memberId,
            WorkspaceMemberRole role,
            Instant joinedAt
    ) {
        return new WorkspaceMember(
                workspaceId,
                memberId,
                role,
                joinedAt
        );
    }

    public void markLastViewed() {
        validateActive();
        lastViewed = true;
    }

    public void clearLastViewed() {
        lastViewed = false;
    }

    public void receiveOwnership() {
        if (!isActive() || role != WorkspaceMemberRole.MEMBER) {
            throw new WorkspaceException(WorkspaceErrorCode.WORKSPACE_OWNERSHIP_TRANSFER_TARGET_CONFLICT);
        }
        this.role = WorkspaceMemberRole.OWNER;
    }

    public void leave(
            Instant leftAt,
            long activeMemberCount
    ) {
        if (!isActive()) {
            return;
        }
        validateLeftAt(leftAt);
        validateLeavePolicy(activeMemberCount);
        this.leftAt = leftAt;
        this.lastViewed = false;
    }

    public void leaveAfterOwnershipTransfer(
            WorkspaceMember successor,
            Instant leftAt
    ) {
        if (!isActive() || role != WorkspaceMemberRole.OWNER) {
            throw new WorkspaceException(WorkspaceErrorCode.WORKSPACE_OWNER_REQUIRED);
        }
        if (successor == null || !successor.isActive() || successor.getRole() != WorkspaceMemberRole.OWNER
                || !workspaceId.equals(successor.getWorkspaceId()) || memberId.equals(successor.getMemberId())) {
            throw new WorkspaceException(WorkspaceErrorCode.WORKSPACE_OWNERSHIP_TRANSFER_TARGET_CONFLICT);
        }
        validateLeftAt(leftAt);
        this.leftAt = leftAt;
        this.lastViewed = false;
    }

    public void validateCanDeleteWorkspace() {
        if (!isActive() || role != WorkspaceMemberRole.OWNER) {
            throw new WorkspaceException(WorkspaceErrorCode.WORKSPACE_OWNER_REQUIRED);
        }
    }

    public void leaveByWorkspaceDeletion(Instant leftAt) {
        validateActive();
        validateLeftAt(leftAt);
        this.leftAt = leftAt;
        this.lastViewed = false;
    }

    public boolean isActive() {
        return leftAt == null;
    }

    private void validateWorkspaceId(Long workspaceId) {
        if (workspaceId == null || workspaceId <= 0) {
            throw new WorkspaceException(WorkspaceErrorCode.INVALID_WORKSPACE_ID);
        }
    }

    private void validateMemberId(Long memberId) {
        if (memberId == null || memberId <= 0) {
            throw new WorkspaceException(WorkspaceErrorCode.INVALID_MEMBER_ID);
        }
    }

    private void validateRole(WorkspaceMemberRole role) {
        if (role == null) {
            throw new WorkspaceException(WorkspaceErrorCode.INVALID_WORKSPACE_MEMBER_ROLE);
        }
    }

    private void validateJoinedAt(Instant joinedAt) {
        if (joinedAt == null) {
            throw new WorkspaceException(WorkspaceErrorCode.INVALID_WORKSPACE_MEMBER_JOINED_AT);
        }
    }

    private void validateLeftAt(Instant leftAt) {
        if (leftAt == null || leftAt.isBefore(joinedAt)) {
            throw new WorkspaceException(WorkspaceErrorCode.INVALID_WORKSPACE_MEMBER_LEFT_AT);
        }
    }

    private void validateLeavePolicy(long activeMemberCount) {
        if (activeMemberCount <= 0) {
            throw new WorkspaceException(WorkspaceErrorCode.INVALID_WORKSPACE_MEMBER_ACTIVE_COUNT);
        }
        if (activeMemberCount > 1 && role == WorkspaceMemberRole.OWNER) {
            throw new WorkspaceException(WorkspaceErrorCode.WORKSPACE_OWNER_TRANSFER_REQUIRED);
        }
    }

    private void validateActive() {
        if (!isActive()) {
            throw new WorkspaceException(WorkspaceErrorCode.WORKSPACE_MEMBER_ALREADY_LEFT);
        }
    }
}
