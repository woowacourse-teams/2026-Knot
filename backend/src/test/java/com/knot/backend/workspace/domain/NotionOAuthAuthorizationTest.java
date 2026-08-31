package com.knot.backend.workspace.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NotionOAuthAuthorizationTest {
    private static final Long WORKSPACE_ID = 1L;
    private static final Long AUTHORIZING_MEMBER_ID = 2L;
    private static final String STATE_HASH = "state-hash";
    private static final URI CALLBACK_URI = URI.create("https://api.knot.test/api/v1/notion/oauth/callback");
    private static final Instant CREATED_AT = Instant.parse("2026-08-31T00:00:00Z");
    private static final Instant EXPIRES_AT = Instant.parse("2026-08-31T00:10:00Z");
    private static final Instant CREATED_AT_WITH_NANOS = Instant.parse("2026-08-31T00:00:00.123456789Z");

    @DisplayName("워크스페이스, 멤버, callback URI와 결합된 OAuth state를 생성한다")
    @Test
    void create_success() {
        // given
        Instant expectedCreatedAt = CREATED_AT_WITH_NANOS.truncatedTo(ChronoUnit.MICROS);

        // when
        NotionOAuthAuthorization authorization = NotionOAuthAuthorization.create(
                WORKSPACE_ID,
                AUTHORIZING_MEMBER_ID,
                STATE_HASH,
                CALLBACK_URI,
                CREATED_AT_WITH_NANOS,
                EXPIRES_AT
        );

        // then
        assertThat(authorization.getWorkspaceId()).isEqualTo(WORKSPACE_ID);
        assertThat(authorization.getAuthorizingMemberId()).isEqualTo(AUTHORIZING_MEMBER_ID);
        assertThat(authorization.getStateHash()).isEqualTo(STATE_HASH);
        assertThat(authorization.getCallbackUri()).isEqualTo(CALLBACK_URI);
        assertThat(authorization.getCreatedAt()).isEqualTo(expectedCreatedAt);
        assertThat(authorization.getExpiresAt()).isEqualTo(EXPIRES_AT);
    }

    @DisplayName("만료 전이고 사용되지 않은 OAuth state는 사용할 수 있다")
    @Test
    void isUsableAt_success() {
        // given
        NotionOAuthAuthorization authorization = createAuthorization();

        // when
        boolean usable = authorization.isUsableAt(CREATED_AT.plusSeconds(1));

        // then
        assertThat(usable).isTrue();
    }

    @DisplayName("만료 시각부터 OAuth state를 사용할 수 없다")
    @Test
    void isUsableAt_failure_expired() {
        // given
        NotionOAuthAuthorization authorization = createAuthorization();

        // when
        boolean usable = authorization.isUsableAt(EXPIRES_AT);

        // then
        assertThat(usable).isFalse();
    }

    @DisplayName("OAuth state를 소비하면 재사용할 수 없다")
    @Test
    void consume_success() {
        // given
        NotionOAuthAuthorization authorization = createAuthorization();
        Instant consumedAt = CREATED_AT.plusSeconds(1);

        // when
        authorization.consume(consumedAt);

        // then
        assertThat(authorization.getConsumedAt()).isEqualTo(consumedAt);
        assertThat(authorization.isUsableAt(consumedAt.plusNanos(1))).isFalse();
    }

    @DisplayName("만료된 OAuth state 소비를 거부한다")
    @Test
    void consume_failure_expired() {
        // given
        NotionOAuthAuthorization authorization = createAuthorization();

        // when
        ThrowingCallable action = () -> authorization.consume(EXPIRES_AT);

        // then
        assertThatThrownBy(action).isInstanceOf(NotionException.class)
                .extracting(exception -> ((NotionException) exception).getErrorCode())
                .isEqualTo(NotionErrorCode.EXPIRED_NOTION_OAUTH_STATE);
    }

    @DisplayName("이미 소비된 OAuth state 재사용을 거부한다")
    @Test
    void consume_failure_alreadyConsumed() {
        // given
        NotionOAuthAuthorization authorization = createAuthorization();
        authorization.consume(CREATED_AT.plusSeconds(1));

        // when
        ThrowingCallable action = () -> authorization.consume(CREATED_AT.plusSeconds(2));

        // then
        assertThatThrownBy(action).isInstanceOf(NotionException.class)
                .extracting(exception -> ((NotionException) exception).getErrorCode())
                .isEqualTo(NotionErrorCode.INVALID_NOTION_OAUTH_STATE);
    }

    @DisplayName("OAuth state를 무효화하면 사용할 수 없다")
    @Test
    void invalidate_success() {
        // given
        NotionOAuthAuthorization authorization = createAuthorization();
        Instant invalidatedAt = CREATED_AT.plusSeconds(1);

        // when
        authorization.invalidate(invalidatedAt);

        // then
        assertThat(authorization.getInvalidatedAt()).isEqualTo(invalidatedAt);
        assertThat(authorization.isUsableAt(invalidatedAt.plusNanos(1))).isFalse();
    }

    @DisplayName("생성 시각보다 이른 시각으로 OAuth state를 무효화할 수 없다")
    @Test
    void invalidate_failure_beforeCreation() {
        // given
        NotionOAuthAuthorization authorization = createAuthorization();

        // when
        ThrowingCallable action = () -> authorization.invalidate(CREATED_AT.minusNanos(1));

        // then
        assertThatThrownBy(action).isInstanceOf(NotionException.class)
                .extracting(exception -> ((NotionException) exception).getErrorCode())
                .isEqualTo(NotionErrorCode.INVALID_NOTION_OAUTH_STATE);
    }

    @DisplayName("해시가 비어 있으면 OAuth state 생성을 거부한다")
    @Test
    void create_failure_blankStateHash() {
        // given
        String blankStateHash = " ";

        // when
        ThrowingCallable action = () -> NotionOAuthAuthorization.create(
                WORKSPACE_ID,
                AUTHORIZING_MEMBER_ID,
                blankStateHash,
                CALLBACK_URI,
                CREATED_AT,
                EXPIRES_AT
        );

        // then
        assertThatThrownBy(action).isInstanceOf(NotionException.class)
                .extracting(exception -> ((NotionException) exception).getErrorCode())
                .isEqualTo(NotionErrorCode.INVALID_NOTION_OAUTH_STATE);
    }

    @DisplayName("만료 시각이 생성 시각보다 늦지 않으면 OAuth state 생성을 거부한다")
    @Test
    void create_failure_invalidExpiration() {
        // given
        Instant invalidExpiresAt = CREATED_AT;

        // when
        ThrowingCallable action = () -> NotionOAuthAuthorization.create(
                WORKSPACE_ID,
                AUTHORIZING_MEMBER_ID,
                STATE_HASH,
                CALLBACK_URI,
                CREATED_AT,
                invalidExpiresAt
        );

        // then
        assertThatThrownBy(action).isInstanceOf(NotionException.class)
                .extracting(exception -> ((NotionException) exception).getErrorCode())
                .isEqualTo(NotionErrorCode.INVALID_NOTION_OAUTH_STATE);
    }

    private NotionOAuthAuthorization createAuthorization() {
        return NotionOAuthAuthorization.create(
                WORKSPACE_ID,
                AUTHORIZING_MEMBER_ID,
                STATE_HASH,
                CALLBACK_URI,
                CREATED_AT,
                EXPIRES_AT
        );
    }
}
