package com.knot.backend.workspace.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class WorkspaceMemberInvitationSourceTest {
    private static final Instant JOINED_AT = Instant.parse("2026-10-03T00:00:00Z");

    @ParameterizedTest
    @EnumSource(WorkspaceMemberRole.class)
    @DisplayName("기존 생성 경로는 생성자와 출처 미확인 참여의 초대를 추측하지 않는다")
    void create_keepsUnknownSourceNull(WorkspaceMemberRole role) {
        // given
        Long workspaceId = 1L;

        // when
        WorkspaceMember member = WorkspaceMember.create(
                workspaceId,
                2L,
                role,
                JOINED_AT
        );

        // then
        assertThat(member.getSourceInvitationId()).isNull();
        assertThat(member.getRole()).isEqualTo(role);
    }

    @Test
    @DisplayName("초대 참여는 출처와 가입 시각을 가진 MEMBER로 생성한다")
    void createFromInvitation_recordsSource() {
        // given
        Long invitationId = 3L;

        // when
        WorkspaceMember member = WorkspaceMember.createFromInvitation(
                1L,
                2L,
                invitationId,
                JOINED_AT
        );

        // then
        assertThat(member.getSourceInvitationId()).isEqualTo(invitationId);
        assertThat(member.getJoinedAt()).isEqualTo(JOINED_AT);
        assertThat(member.getRole()).isEqualTo(WorkspaceMemberRole.MEMBER);
        assertThat(member.isLastViewed()).isFalse();
        assertThat(member.isActive()).isTrue();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {0, -1})
    @DisplayName("초대 참여 생성은 누락되거나 양수가 아닌 초대 ID를 거부한다")
    void createFromInvitation_rejectsInvalidSource(Long invitationId) {
        // given
        Long workspaceId = 1L;

        // when
        ThrowingCallable action = () -> WorkspaceMember.createFromInvitation(
                workspaceId,
                2L,
                invitationId,
                JOINED_AT
        );

        // then
        assertThatThrownBy(action).isInstanceOf(WorkspaceException.class)
                .satisfies(
                        error -> assertThat(((WorkspaceException) error).getErrorCode())
                                .isEqualTo(WorkspaceErrorCode.INVALID_WORKSPACE_MEMBER_SOURCE_INVITATION_ID)
                );
    }

    @Test
    @DisplayName("OWNER 권한을 받아도 가입 초대 출처는 보존한다")
    void receiveOwnership_preservesSource() {
        // given
        WorkspaceMember member = invitedMember();

        // when
        member.receiveOwnership();

        // then
        assertThat(member.getRole()).isEqualTo(WorkspaceMemberRole.OWNER);
        assertThat(member.getSourceInvitationId()).isEqualTo(3L);
        assertThat(member.getJoinedAt()).isEqualTo(JOINED_AT);
    }

    @Test
    @DisplayName("탈퇴는 가입 출처를 보존하고 마지막 조회 상태만 해제한다")
    void leave_preservesSource() {
        // given
        WorkspaceMember member = invitedMember();
        member.markLastViewed();

        // when
        member.leave(
                JOINED_AT.plusSeconds(1),
                2
        );

        // then
        assertThat(member.getSourceInvitationId()).isEqualTo(3L);
        assertThat(member.getJoinedAt()).isEqualTo(JOINED_AT);
        assertThat(member.isLastViewed()).isFalse();
        assertThat(member.isActive()).isFalse();
    }

    private WorkspaceMember invitedMember() {
        return WorkspaceMember.createFromInvitation(
                1L,
                2L,
                3L,
                JOINED_AT
        );
    }
}
