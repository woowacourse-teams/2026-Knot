package com.knot.backend.workspace.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WorkspaceMemberTest {
    private static final Instant JOINED_AT = Instant.parse("2026-08-24T00:00:00Z");

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
}
