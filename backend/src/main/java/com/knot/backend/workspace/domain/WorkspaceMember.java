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
        lastViewed = true;
    }

    public void clearLastViewed() {
        lastViewed = false;
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
}
