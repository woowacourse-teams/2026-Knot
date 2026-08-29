package com.knot.backend.workspace.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WorkspaceInvitationTest {
    private static final Instant CREATED_AT = Instant.parse("2026-08-29T00:00:00Z");
    private static final String LINK_TOKEN_HASH = "link-token-hash";
    private static final String INVITE_CODE_HASH = "invite-code-hash";

    @DisplayName("링크 토큰 해시와 초대 코드 해시로 24시간짜리 워크스페이스 초대를 생성한다")
    @Test
    void create_success() {
        // given
        Long workspaceId = 1L;

        // when
        WorkspaceInvitation invitation = WorkspaceInvitation.create(
                workspaceId,
                LINK_TOKEN_HASH,
                INVITE_CODE_HASH,
                CREATED_AT
        );

        // then
        assertThat(invitation.getWorkspaceId()).isEqualTo(workspaceId);
        assertThat(invitation.getLinkTokenHash()).isEqualTo(LINK_TOKEN_HASH);
        assertThat(invitation.getInviteCodeHash()).isEqualTo(INVITE_CODE_HASH);
        assertThat(invitation.getExpiresAt()).isEqualTo(CREATED_AT.plus(WorkspaceInvitation.VALIDITY_PERIOD));
        assertThat(invitation.getInvalidatedAt()).isNull();
        assertThat(invitation.getCreatedAt()).isEqualTo(CREATED_AT);
    }

    @DisplayName("워크스페이스 ID가 양수가 아니면 초대 생성을 거부한다")
    @Test
    void create_failure_invalidWorkspaceId() {
        // given
        Long invalidWorkspaceId = 0L;

        // when
        ThrowingCallable action = () -> WorkspaceInvitation.create(
                invalidWorkspaceId,
                LINK_TOKEN_HASH,
                INVITE_CODE_HASH,
                CREATED_AT
        );

        // then
        assertThatThrownBy(action).isInstanceOf(WorkspaceException.class)
                .extracting(exception -> ((WorkspaceException) exception).getErrorCode())
                .isEqualTo(WorkspaceErrorCode.INVALID_WORKSPACE_ID);
    }

    @DisplayName("링크 토큰 해시가 비어 있으면 초대 생성을 거부한다")
    @Test
    void create_failure_blankLinkTokenHash() {
        // given
        String blankLinkTokenHash = " ";

        // when
        ThrowingCallable action = () -> WorkspaceInvitation.create(
                1L,
                blankLinkTokenHash,
                INVITE_CODE_HASH,
                CREATED_AT
        );

        // then
        assertThatThrownBy(action).isInstanceOf(WorkspaceException.class)
                .extracting(exception -> ((WorkspaceException) exception).getErrorCode())
                .isEqualTo(WorkspaceErrorCode.INVALID_WORKSPACE_INVITATION_LINK_TOKEN_HASH);
    }

    @DisplayName("링크 토큰 해시가 저장 길이를 넘으면 초대 생성을 거부한다")
    @Test
    void create_failure_tooLongLinkTokenHash() {
        // given
        String tooLongLinkTokenHash = "a".repeat(WorkspaceInvitation.MAX_HASH_LENGTH + 1);

        // when
        ThrowingCallable action = () -> WorkspaceInvitation.create(
                1L,
                tooLongLinkTokenHash,
                INVITE_CODE_HASH,
                CREATED_AT
        );

        // then
        assertThatThrownBy(action).isInstanceOf(WorkspaceException.class)
                .extracting(exception -> ((WorkspaceException) exception).getErrorCode())
                .isEqualTo(WorkspaceErrorCode.INVALID_WORKSPACE_INVITATION_LINK_TOKEN_HASH);
    }

    @DisplayName("초대 코드 해시가 비어 있으면 초대 생성을 거부한다")
    @Test
    void create_failure_blankInviteCodeHash() {
        // given
        String blankInviteCodeHash = " ";

        // when
        ThrowingCallable action = () -> WorkspaceInvitation.create(
                1L,
                LINK_TOKEN_HASH,
                blankInviteCodeHash,
                CREATED_AT
        );

        // then
        assertThatThrownBy(action).isInstanceOf(WorkspaceException.class)
                .extracting(exception -> ((WorkspaceException) exception).getErrorCode())
                .isEqualTo(WorkspaceErrorCode.INVALID_WORKSPACE_INVITATION_CODE_HASH);
    }

    @DisplayName("초대 코드 해시가 저장 길이를 넘으면 초대 생성을 거부한다")
    @Test
    void create_failure_tooLongInviteCodeHash() {
        // given
        String tooLongInviteCodeHash = "a".repeat(WorkspaceInvitation.MAX_HASH_LENGTH + 1);

        // when
        ThrowingCallable action = () -> WorkspaceInvitation.create(
                1L,
                LINK_TOKEN_HASH,
                tooLongInviteCodeHash,
                CREATED_AT
        );

        // then
        assertThatThrownBy(action).isInstanceOf(WorkspaceException.class)
                .extracting(exception -> ((WorkspaceException) exception).getErrorCode())
                .isEqualTo(WorkspaceErrorCode.INVALID_WORKSPACE_INVITATION_CODE_HASH);
    }

    @DisplayName("생성 시각이 없으면 초대 생성을 거부한다")
    @Test
    void create_failure_missingCreatedAt() {
        // given
        Instant missingCreatedAt = null;

        // when
        ThrowingCallable action = () -> WorkspaceInvitation.create(
                1L,
                LINK_TOKEN_HASH,
                INVITE_CODE_HASH,
                missingCreatedAt
        );

        // then
        assertThatThrownBy(action).isInstanceOf(WorkspaceException.class)
                .extracting(exception -> ((WorkspaceException) exception).getErrorCode())
                .isEqualTo(WorkspaceErrorCode.INVALID_WORKSPACE_INVITATION_CREATED_AT);
    }

    @DisplayName("생성 이후 만료 전 시점에는 초대가 유효하다")
    @Test
    void isValidAt_success_beforeExpiration() {
        // given
        WorkspaceInvitation invitation = createInvitation();
        Instant pointInTime = CREATED_AT.plusSeconds(1);

        // when
        boolean valid = invitation.isValidAt(pointInTime);

        // then
        assertThat(valid).isTrue();
    }

    @DisplayName("생성 전 시점에는 초대가 유효하지 않다")
    @Test
    void isValidAt_failure_beforeCreation() {
        // given
        WorkspaceInvitation invitation = createInvitation();
        Instant pointInTime = CREATED_AT.minusNanos(1);

        // when
        boolean valid = invitation.isValidAt(pointInTime);

        // then
        assertThat(valid).isFalse();
    }

    @DisplayName("만료 시각부터 초대가 유효하지 않다")
    @Test
    void isValidAt_failure_atExpiration() {
        // given
        WorkspaceInvitation invitation = createInvitation();

        // when
        boolean valid = invitation.isValidAt(invitation.getExpiresAt());

        // then
        assertThat(valid).isFalse();
    }

    @DisplayName("무효화 시각 전에는 유효하고 무효화 시각부터 유효하지 않다")
    @Test
    void invalidate_success() {
        // given
        WorkspaceInvitation invitation = createInvitation();
        Instant invalidatedAt = CREATED_AT.plusSeconds(1);

        // when
        invitation.invalidate(invalidatedAt);

        // then
        assertThat(invitation.getInvalidatedAt()).isEqualTo(invalidatedAt);
        assertThat(invitation.isValidAt(invalidatedAt.minusNanos(1))).isTrue();
        assertThat(invitation.isValidAt(invalidatedAt)).isFalse();
    }

    @DisplayName("이미 무효화된 초대를 다시 무효화해도 최초 무효화 시각을 유지한다")
    @Test
    void invalidate_success_alreadyInvalidated() {
        // given
        WorkspaceInvitation invitation = createInvitation();
        Instant firstInvalidatedAt = CREATED_AT.plusSeconds(1);
        invitation.invalidate(firstInvalidatedAt);

        // when
        invitation.invalidate(firstInvalidatedAt.plusSeconds(1));

        // then
        assertThat(invitation.getInvalidatedAt()).isEqualTo(firstInvalidatedAt);
    }

    @DisplayName("생성 시각보다 이른 시각으로 초대를 무효화할 수 없다")
    @Test
    void invalidate_failure_beforeCreation() {
        // given
        WorkspaceInvitation invitation = createInvitation();
        Instant invalidatedAt = CREATED_AT.minusNanos(1);

        // when
        ThrowingCallable action = () -> invitation.invalidate(invalidatedAt);

        // then
        assertThatThrownBy(action).isInstanceOf(WorkspaceException.class)
                .extracting(exception -> ((WorkspaceException) exception).getErrorCode())
                .isEqualTo(WorkspaceErrorCode.INVALID_WORKSPACE_INVITATION_INVALIDATED_AT);
    }

    @DisplayName("무효화 시각이 없으면 초대를 무효화할 수 없다")
    @Test
    void invalidate_failure_missingInvalidatedAt() {
        // given
        WorkspaceInvitation invitation = createInvitation();
        Instant missingInvalidatedAt = null;

        // when
        ThrowingCallable action = () -> invitation.invalidate(missingInvalidatedAt);

        // then
        assertThatThrownBy(action).isInstanceOf(WorkspaceException.class)
                .extracting(exception -> ((WorkspaceException) exception).getErrorCode())
                .isEqualTo(WorkspaceErrorCode.INVALID_WORKSPACE_INVITATION_INVALIDATED_AT);
    }

    @DisplayName("확인 시각이 없으면 초대 유효성을 판단할 수 없다")
    @Test
    void isValidAt_failure_missingPointInTime() {
        // given
        WorkspaceInvitation invitation = createInvitation();
        Instant missingPointInTime = null;

        // when
        ThrowingCallable action = () -> invitation.isValidAt(missingPointInTime);

        // then
        assertThatThrownBy(action).isInstanceOf(WorkspaceException.class)
                .extracting(exception -> ((WorkspaceException) exception).getErrorCode())
                .isEqualTo(WorkspaceErrorCode.INVALID_WORKSPACE_INVITATION_POINT_IN_TIME);
    }

    private WorkspaceInvitation createInvitation() {
        return WorkspaceInvitation.create(
                1L,
                LINK_TOKEN_HASH,
                INVITE_CODE_HASH,
                CREATED_AT
        );
    }
}
