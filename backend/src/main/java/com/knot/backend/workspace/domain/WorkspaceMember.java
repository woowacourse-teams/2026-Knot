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

@Entity
@Table(name = "workspace_members")
public class WorkspaceMember {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "workspace_id", nullable = false) private Long workspaceId;
    @Column(name = "member_id", nullable = false) private Long memberId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20) private WorkspaceMemberRole role;
    @Column(name = "joined_at", nullable = false, updatable = false) private Instant joinedAt;

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

    public Long getId() {
        return id;
    }

    public Long getWorkspaceId() {
        return workspaceId;
    }

    public Long getMemberId() {
        return memberId;
    }

    public WorkspaceMemberRole getRole() {
        return role;
    }

    public Instant getJoinedAt() {
        return joinedAt;
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
