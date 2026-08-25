package com.knot.backend.workspace.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WorkspaceTest {
    private static final Instant CREATED_AT = Instant.parse("2026-08-24T00:00:00Z");

    @DisplayName("유효한 이름으로 워크스페이스를 생성한다")
    @Test
    void createWorkspace() {
        // given
        String name = "Knot 팀";

        // when
        Workspace workspace = Workspace.create(
                name,
                CREATED_AT
        );

        // then
        assertThat(workspace.getName()).isEqualTo(name);
        assertThat(workspace.getCreatedAt()).isEqualTo(CREATED_AT);
    }

    @DisplayName("워크스페이스 이름은 최대 20자까지 허용한다")
    @Test
    void createWorkspaceWithMaximumLengthName() {
        // given
        String maximumLengthName = "가".repeat(Workspace.MAX_NAME_LENGTH);

        // when
        Workspace workspace = Workspace.create(
                maximumLengthName,
                CREATED_AT
        );

        // then
        assertThat(workspace.getName()).isEqualTo(maximumLengthName);
    }

    @DisplayName("워크스페이스 이름이 비어 있으면 생성을 거부한다")
    @Test
    void rejectBlankName() {
        // given
        String blankName = " ";

        // when
        ThrowingCallable action = () -> Workspace.create(
                blankName,
                CREATED_AT
        );

        // then
        assertThatThrownBy(action).isInstanceOf(WorkspaceException.class)
                .extracting(exception -> ((WorkspaceException) exception).getErrorCode())
                .isEqualTo(WorkspaceErrorCode.INVALID_WORKSPACE_NAME);
    }

    @DisplayName("워크스페이스 이름이 최대 길이를 넘으면 생성을 거부한다")
    @Test
    void rejectTooLongName() {
        // given
        String tooLongName = "가".repeat(Workspace.MAX_NAME_LENGTH + 1);

        // when
        ThrowingCallable action = () -> Workspace.create(
                tooLongName,
                CREATED_AT
        );

        // then
        assertThatThrownBy(action).isInstanceOf(WorkspaceException.class)
                .extracting(exception -> ((WorkspaceException) exception).getErrorCode())
                .isEqualTo(WorkspaceErrorCode.INVALID_WORKSPACE_NAME);
    }

    @DisplayName("워크스페이스 이름에 특수문자가 있으면 생성을 거부한다")
    @Test
    void rejectNameContainingSpecialCharacter() {
        // given
        String nameContainingSpecialCharacter = "Knot_팀";

        // when
        ThrowingCallable action = () -> Workspace.create(
                nameContainingSpecialCharacter,
                CREATED_AT
        );

        // then
        assertThatThrownBy(action).isInstanceOf(WorkspaceException.class)
                .extracting(exception -> ((WorkspaceException) exception).getErrorCode())
                .isEqualTo(WorkspaceErrorCode.INVALID_WORKSPACE_NAME);
    }

    @DisplayName("생성 시각이 없으면 워크스페이스 생성을 거부한다")
    @Test
    void rejectMissingCreatedAt() {
        // given
        Instant missingCreatedAt = null;

        // when
        ThrowingCallable action = () -> Workspace.create(
                "Knot 팀",
                missingCreatedAt
        );

        // then
        assertThatThrownBy(action).isInstanceOf(WorkspaceException.class)
                .extracting(exception -> ((WorkspaceException) exception).getErrorCode())
                .isEqualTo(WorkspaceErrorCode.INVALID_WORKSPACE_CREATED_AT);
    }
}
