package com.knot.backend.workspace.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.regex.Pattern;
import lombok.Getter;

@Getter
@Entity
@Table(name = "workspaces")
public class Workspace {
    public static final int MAX_NAME_LENGTH = 20;
    public static final String NAME_PATTERN_REGEX = "^(?=.*[가-힣A-Za-z])[가-힣A-Za-z ]+$";
    private static final Pattern NAME_PATTERN = Pattern.compile(NAME_PATTERN_REGEX);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = MAX_NAME_LENGTH)
    private String name;

    @Column(name = "created_by_member_id", nullable = false, updatable = false)
    private Long createdByMemberId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected Workspace() {}

    private Workspace(
            String name,
            Long createdByMemberId,
            Instant createdAt
    ) {
        validateName(name);
        validateCreator(createdByMemberId);
        validateCreatedAt(createdAt);
        this.name = name;
        this.createdByMemberId = createdByMemberId;
        this.createdAt = createdAt;
    }

    public static Workspace create(
            String name,
            Long createdByMemberId,
            Instant createdAt
    ) {
        return new Workspace(
                name,
                createdByMemberId,
                createdAt
        );
    }

    private void validateCreator(Long createdByMemberId) {
        if (createdByMemberId == null || createdByMemberId <= 0) {
            throw new WorkspaceException(WorkspaceErrorCode.INVALID_MEMBER_ID);
        }
    }

    private void validateName(String name) {
        if (name == null || name.isBlank() || name.length() > MAX_NAME_LENGTH || !NAME_PATTERN.matcher(name)
                .matches()) {
            throw new WorkspaceException(WorkspaceErrorCode.INVALID_WORKSPACE_NAME);
        }
    }

    private void validateCreatedAt(Instant createdAt) {
        if (createdAt == null) {
            throw new WorkspaceException(WorkspaceErrorCode.INVALID_WORKSPACE_CREATED_AT);
        }
    }
}
