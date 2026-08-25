package com.knot.backend.workspace.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.regex.Pattern;

@Entity
@Table(name = "workspaces")
public class Workspace {
    public static final int MAX_NAME_LENGTH = 20;
    private static final Pattern NAME_PATTERN = Pattern.compile("^[가-힣A-Za-z ]+$");
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, length = MAX_NAME_LENGTH) private String name;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;

    protected Workspace() {}

    private Workspace(
            String name,
            Instant createdAt
    ) {
        validateName(name);
        validateCreatedAt(createdAt);
        this.name = name;
        this.createdAt = createdAt;
    }

    public static Workspace create(
            String name,
            Instant createdAt
    ) {
        return new Workspace(
                name,
                createdAt
        );
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    private void validateName(String name) {
        if (name == null || name.isBlank() || name.length() > MAX_NAME_LENGTH
                || !NAME_PATTERN.matcher(name)
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
