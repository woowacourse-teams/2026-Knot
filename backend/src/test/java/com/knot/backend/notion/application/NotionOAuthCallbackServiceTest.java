package com.knot.backend.notion.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.knot.backend.notion.application.dto.result.NotionOAuthAuthorizationContext;
import com.knot.backend.notion.application.dto.result.NotionOAuthToken;
import com.knot.backend.notion.domain.NotionErrorCode;
import com.knot.backend.notion.domain.NotionException;
import java.net.URI;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NotionOAuthCallbackServiceTest {
    private static final String CODE = "oauth-code";
    private static final String STATE = "oauth-state";
    private static final URI CALLBACK_URI = URI.create("https://api.knot.test/api/v1/notion/oauth/callback");
    private static final NotionOAuthAuthorizationContext AUTHORIZATION = new NotionOAuthAuthorizationContext(
            7L,
            1L,
            2L,
            CALLBACK_URI
    );
    private static final NotionOAuthToken TOKEN = new NotionOAuthToken(
            "access-token",
            "refresh-token",
            "notion-workspace-id",
            "Knot Notion",
            null,
            "bot-id",
            "user",
            "notion-owner-user-id",
            null,
            null
    );
    private final NotionOAuthAuthorizationService authorizationService = mock(NotionOAuthAuthorizationService.class);
    private final NotionOAuthClient oAuthClient = mock(NotionOAuthClient.class);
    private final NotionConnectionService connectionService = mock(NotionConnectionService.class);
    private final NotionOAuthCallbackService service = new NotionOAuthCallbackService(
            authorizationService,
            oAuthClient,
            connectionService
    );

    @DisplayName("callback 성공 시 토큰을 교환하고 Notion connection을 저장한다")
    @Test
    void complete_success_connectsWorkspace() {
        // given
        when(authorizationService.consume(STATE)).thenReturn(AUTHORIZATION);
        when(
                oAuthClient.exchange(
                        CODE,
                        CALLBACK_URI
                )
        ).thenReturn(TOKEN);

        // when
        boolean completed = service.complete(
                CODE,
                STATE,
                null
        );

        // then
        assertThat(completed).isTrue();
        verify(connectionService).connect(
                AUTHORIZATION,
                TOKEN
        );
    }

    @DisplayName("Notion이 callback error를 반환하면 connection을 저장하지 않는다")
    @Test
    void complete_failure_callbackErrorDoesNotConnect() {
        // given
        when(authorizationService.consume(STATE)).thenReturn(AUTHORIZATION);

        // when
        boolean completed = service.complete(
                CODE,
                STATE,
                "access_denied"
        );

        // then
        assertThat(completed).isFalse();
        verify(authorizationService).consume(STATE);
        verifyNoInteractions(oAuthClient);
        verify(
                connectionService,
                never()
        ).connect(
                AUTHORIZATION,
                TOKEN
        );
    }

    @DisplayName("토큰 교환 실패 시 connection을 저장하지 않는다")
    @Test
    void complete_failure_tokenExchangeDoesNotConnect() {
        // given
        when(authorizationService.consume(STATE)).thenReturn(AUTHORIZATION);
        when(
                oAuthClient.exchange(
                        CODE,
                        CALLBACK_URI
                )
        ).thenThrow(new NotionException(NotionErrorCode.NOTION_OAUTH_TOKEN_EXCHANGE_FAILED));

        // when
        boolean completed = service.complete(
                CODE,
                STATE,
                null
        );

        // then
        assertThat(completed).isFalse();
        verify(authorizationService).consume(STATE);
        verify(
                connectionService,
                never()
        ).connect(
                AUTHORIZATION,
                TOKEN
        );
    }

    @DisplayName("유효하지 않은 state면 connection을 저장하지 않는다")
    @Test
    void complete_failure_invalidStateDoesNotConnect() {
        // given
        when(authorizationService.consume(STATE))
                .thenThrow(new NotionException(NotionErrorCode.INVALID_NOTION_OAUTH_STATE));

        // when
        boolean completed = service.complete(
                CODE,
                STATE,
                null
        );

        // then
        assertThat(completed).isFalse();
        verifyNoInteractions(
                oAuthClient,
                connectionService
        );
    }
}
