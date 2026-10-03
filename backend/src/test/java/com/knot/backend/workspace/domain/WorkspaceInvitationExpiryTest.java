package com.knot.backend.workspace.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

class WorkspaceInvitationExpiryTest {
    private static final Long WORKSPACE_ID = 1L;
    private static final String LINK_TOKEN_HASH = "link-token-hash";
    private static final String INVITE_CODE_HASH = "invite-code-hash";
    private static final String LINK_TOKEN_CIPHERTEXT = "link-token-ciphertext";
    private static final String INVITE_CODE_CIPHERTEXT = "invite-code-ciphertext";
    private static final Instant CREATED_AT = Instant.parse("2026-08-29T00:00:00Z");
    private static final Instant EARLY_EXPIRES_AT = CREATED_AT.plusSeconds(60);
    private static final Instant LATE_EXPIRES_AT = CREATED_AT.plusSeconds(120);

    @DisplayName("초대 코드와 링크 토큰은 서로 다른 만료 시각을 가진다")
    @Test
    void createWithExpirations_success_independentExpirations() {
        // given
        Instant expectedLegacyExpiresAt = CREATED_AT.plus(WorkspaceInvitation.VALIDITY_PERIOD);

        // when
        WorkspaceInvitation invitation = createInvitation(
                EARLY_EXPIRES_AT,
                LATE_EXPIRES_AT
        );

        // then
        assertThat(invitation.getCodeExpiresAt()).isEqualTo(EARLY_EXPIRES_AT);
        assertThat(invitation.getLinkTokenExpiresAt()).isEqualTo(LATE_EXPIRES_AT);
        assertThat(invitation.getExpiresAt()).isEqualTo(expectedLegacyExpiresAt);
    }

    @DisplayName("기존 생성 경로는 초대 코드와 링크 토큰 모두 24시간 만료를 사용한다")
    @Test
    void create_success_defaultChannelExpirations() {
        // given
        Instant expectedExpiresAt = CREATED_AT.plus(WorkspaceInvitation.VALIDITY_PERIOD);

        // when
        WorkspaceInvitation invitation = WorkspaceInvitation.create(
                WORKSPACE_ID,
                LINK_TOKEN_HASH,
                INVITE_CODE_HASH,
                CREATED_AT
        );

        // then
        assertThat(invitation.getCodeExpiresAt()).isEqualTo(expectedExpiresAt);
        assertThat(invitation.getLinkTokenExpiresAt()).isEqualTo(expectedExpiresAt);
    }

    @DisplayName("수단별 초대는 생성 이후부터 자기 만료 시각 직전까지만 유효하다")
    @ParameterizedTest(name = "{0}")
    @CsvSource({"초대 코드 생성 전, CODE, 60, 120, BEFORE_CREATION, false",
            "초대 코드 만료 직전, CODE, 60, 120, BEFORE_EXPIRATION, true", "초대 코드 만료 시각, CODE, 60, 120, AT_EXPIRATION, false",
            "초대 코드 만료 이후, CODE, 60, 120, AFTER_EXPIRATION, false",
            "링크 생성 전, LINK_TOKEN, 120, 60, BEFORE_CREATION, false",
            "링크 만료 직전, LINK_TOKEN, 120, 60, BEFORE_EXPIRATION, true",
            "링크 만료 시각, LINK_TOKEN, 120, 60, AT_EXPIRATION, false",
            "링크 만료 이후, LINK_TOKEN, 120, 60, AFTER_EXPIRATION, false",
            "초대 코드 만료 뒤 링크 토큰, LINK_TOKEN, 60, 120, AT_EARLY_EXPIRATION, true",
            "링크 토큰 만료 뒤 초대 코드, CODE, 120, 60, AT_EARLY_EXPIRATION, true"})
    void isChannelValidAt_success_withIndependentExpiration(
            String name,
            Channel channel,
            long codeExpiresAfterSeconds,
            long linkTokenExpiresAfterSeconds,
            PointCase pointCase,
            boolean expected
    ) {
        // given
        Instant codeExpiresAt = CREATED_AT.plusSeconds(codeExpiresAfterSeconds);
        Instant linkTokenExpiresAt = CREATED_AT.plusSeconds(linkTokenExpiresAfterSeconds);
        WorkspaceInvitation invitation = createInvitation(
                codeExpiresAt,
                linkTokenExpiresAt
        );

        // when
        boolean valid = isValidAt(
                invitation,
                channel,
                pointInTime(pointCase)
        );

        // then
        assertThat(valid).isEqualTo(expected);
    }

    @DisplayName("무효화된 초대는 수단별 만료 시각과 무관하게 유효하지 않다")
    @ParameterizedTest
    @EnumSource(Channel.class)
    void isChannelValidAt_failure_invalidated(Channel channel) {
        // given
        WorkspaceInvitation invitation = createInvitation(
                EARLY_EXPIRES_AT,
                LATE_EXPIRES_AT
        );
        Instant invalidatedAt = CREATED_AT.plusSeconds(1);
        invitation.invalidate(invalidatedAt);

        // when
        boolean valid = isValidAt(
                invitation,
                channel,
                invalidatedAt.minusNanos(1)
        );

        // then
        assertThat(valid).isFalse();
    }

    @DisplayName("확인 시각이 없으면 수단별 초대 유효성을 판단할 수 없다")
    @ParameterizedTest
    @EnumSource(Channel.class)
    void isChannelValidAt_failure_missingPointInTime(Channel channel) {
        // given
        WorkspaceInvitation invitation = createInvitation(
                EARLY_EXPIRES_AT,
                LATE_EXPIRES_AT
        );
        Instant missingPointInTime = null;

        // when
        ThrowingCallable action = () -> isValidAt(
                invitation,
                channel,
                missingPointInTime
        );

        // then
        assertThatThrownBy(action).isInstanceOf(WorkspaceException.class)
                .extracting(exception -> ((WorkspaceException) exception).getErrorCode())
                .isEqualTo(WorkspaceErrorCode.INVALID_WORKSPACE_INVITATION_POINT_IN_TIME);
    }

    @DisplayName("수단별 만료 시각이 없거나 생성 시각보다 늦지 않으면 초대 생성을 거부한다")
    @ParameterizedTest(name = "{0}")
    @CsvSource({"초대 코드 만료 시각 없음, MISSING_CODE", "링크 만료 시각 없음, MISSING_LINK", "링크 만료가 생성 전, LINK_BEFORE_CREATED",
            "초대 코드 만료 시각이 생성 시각과 같음, EQUAL_CREATED", "마이크로초 보정 뒤 생성 시각과 같음, COLLAPSED_TO_CREATED"})
    void createWithExpirations_failure_invalidExpiresAt(
            String name,
            InvalidExpiryCase invalidExpiryCase
    ) {
        // given
        ExpiryFixture fixture = expiryFixture(invalidExpiryCase);

        // when
        ThrowingCallable action = () -> WorkspaceInvitation.createWithExpirations(
                WORKSPACE_ID,
                LINK_TOKEN_HASH,
                INVITE_CODE_HASH,
                LINK_TOKEN_CIPHERTEXT,
                INVITE_CODE_CIPHERTEXT,
                fixture.createdAt(),
                fixture.codeExpiresAt(),
                fixture.linkTokenExpiresAt()
        );

        // then
        assertThatThrownBy(action).isInstanceOf(WorkspaceException.class)
                .extracting(exception -> ((WorkspaceException) exception).getErrorCode())
                .isEqualTo(WorkspaceErrorCode.INVALID_WORKSPACE_INVITATION_EXPIRES_AT);
    }

    private boolean isValidAt(
            WorkspaceInvitation invitation,
            Channel channel,
            Instant pointInTime
    ) {
        if (channel == Channel.CODE) {
            return invitation.isCodeValidAt(pointInTime);
        }
        return invitation.isLinkTokenValidAt(pointInTime);
    }

    private Instant pointInTime(PointCase pointCase) {
        return switch (pointCase) {
            case BEFORE_CREATION -> CREATED_AT.minusNanos(1);
            case BEFORE_EXPIRATION -> EARLY_EXPIRES_AT.minusNanos(1);
            case AT_EXPIRATION, AT_EARLY_EXPIRATION -> EARLY_EXPIRES_AT;
            case AFTER_EXPIRATION -> EARLY_EXPIRES_AT.plusNanos(1);
        };
    }

    private ExpiryFixture expiryFixture(InvalidExpiryCase invalidExpiryCase) {
        return switch (invalidExpiryCase) {
            case MISSING_CODE -> new ExpiryFixture(
                    CREATED_AT,
                    null,
                    LATE_EXPIRES_AT
            );
            case MISSING_LINK -> new ExpiryFixture(
                    CREATED_AT,
                    EARLY_EXPIRES_AT,
                    null
            );
            case LINK_BEFORE_CREATED -> new ExpiryFixture(
                    CREATED_AT,
                    EARLY_EXPIRES_AT,
                    CREATED_AT.minusSeconds(1)
            );
            case EQUAL_CREATED -> new ExpiryFixture(
                    CREATED_AT,
                    CREATED_AT,
                    LATE_EXPIRES_AT
            );
            case COLLAPSED_TO_CREATED -> new ExpiryFixture(
                    Instant.parse("2026-08-29T00:00:00.123456789Z"),
                    Instant.parse("2026-08-29T00:00:00.123456999Z"),
                    LATE_EXPIRES_AT
            );
        };
    }

    private WorkspaceInvitation createInvitation(
            Instant codeExpiresAt,
            Instant linkTokenExpiresAt
    ) {
        return WorkspaceInvitation.createWithExpirations(
                WORKSPACE_ID,
                LINK_TOKEN_HASH,
                INVITE_CODE_HASH,
                LINK_TOKEN_CIPHERTEXT,
                INVITE_CODE_CIPHERTEXT,
                CREATED_AT,
                codeExpiresAt,
                linkTokenExpiresAt
        );
    }

    private enum Channel {
        CODE,
        LINK_TOKEN
    }

    private enum PointCase {
        BEFORE_CREATION,
        BEFORE_EXPIRATION,
        AT_EXPIRATION,
        AFTER_EXPIRATION,
        AT_EARLY_EXPIRATION
    }

    private enum InvalidExpiryCase {
        MISSING_CODE,
        MISSING_LINK,
        LINK_BEFORE_CREATED,
        EQUAL_CREATED,
        COLLAPSED_TO_CREATED
    }

    private record ExpiryFixture(
            Instant createdAt,
            Instant codeExpiresAt,
            Instant linkTokenExpiresAt
    ) {
    }
}
