package com.knot.backend.workspace.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.knot.backend.workspace.application.dto.result.ContentSourceAuthorizationContext;
import com.knot.backend.workspace.application.dto.result.AuthorizedContentSource;
import com.knot.backend.workspace.domain.ContentSourceAuthorizationOwnerType;
import com.knot.backend.workspace.domain.ContentSourceErrorCode;
import com.knot.backend.workspace.domain.ContentSourceException;
import com.knot.backend.workspace.domain.ContentSourceProvider;
import java.net.URI;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ContentSourceCallbackServiceTest {
    private static final String CODE = "oauth-code";
    private static final String STATE = "oauth-state";
    private static final URI CALLBACK_URI = URI.create("https://api.knot.test/api/v1/notion/oauth/callback");
    private static final ContentSourceAuthorizationContext AUTHORIZATION = new ContentSourceAuthorizationContext(
            7L,
            1L,
            ContentSourceProvider.NOTION,
            2L,
            CALLBACK_URI
    );
    private static final AuthorizedContentSource TOKEN = new AuthorizedContentSource(
            ContentSourceProvider.NOTION,
            "access-token",
            "refresh-token",
            "notion-workspace-id",
            "Knot Notion",
            null,
            "bot-id",
            ContentSourceAuthorizationOwnerType.USER,
            "notion-owner-user-id",
            null,
            null
    );
    private final ContentSourceAuthorizationService authorizationService = mock(
            ContentSourceAuthorizationService.class
    );
    private final ContentSourceAuthorizationClient oAuthClient = mock(ContentSourceAuthorizationClient.class);
    private final ContentSourceConnectionService connectionService = mock(ContentSourceConnectionService.class);
    private final ContentSourceCallbackService service = new ContentSourceCallbackService(
            authorizationService,
            oAuthClient,
            connectionService
    );

    @DisplayName("callback 성공 시 토큰을 교환하고 Notion connection을 저장한다")
    @Test
    void complete_success_connectsWorkspace() {
        // given
        when(
                authorizationService.consume(
                        ContentSourceProvider.NOTION,
                        STATE
                )
        ).thenReturn(AUTHORIZATION);
        when(
                oAuthClient.exchange(
                        ContentSourceProvider.NOTION,
                        CODE,
                        CALLBACK_URI
                )
        ).thenReturn(TOKEN);

        // when
        boolean completed = service.complete(
                ContentSourceProvider.NOTION,
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
        when(
                authorizationService.consume(
                        ContentSourceProvider.NOTION,
                        STATE
                )
        ).thenReturn(AUTHORIZATION);

        // when
        boolean completed = service.complete(
                ContentSourceProvider.NOTION,
                CODE,
                STATE,
                "access_denied"
        );

        // then
        assertThat(completed).isFalse();
        verify(authorizationService).consume(
                ContentSourceProvider.NOTION,
                STATE
        );
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
        when(
                authorizationService.consume(
                        ContentSourceProvider.NOTION,
                        STATE
                )
        ).thenReturn(AUTHORIZATION);
        when(
                oAuthClient.exchange(
                        ContentSourceProvider.NOTION,
                        CODE,
                        CALLBACK_URI
                )
        ).thenThrow(new ContentSourceException(ContentSourceErrorCode.CONTENT_SOURCE_AUTHORIZATION_FAILED));

        // when
        boolean completed = service.complete(
                ContentSourceProvider.NOTION,
                CODE,
                STATE,
                null
        );

        // then
        assertThat(completed).isFalse();
        verify(authorizationService).consume(
                ContentSourceProvider.NOTION,
                STATE
        );
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
        when(
                authorizationService.consume(
                        ContentSourceProvider.NOTION,
                        STATE
                )
        ).thenThrow(new ContentSourceException(ContentSourceErrorCode.INVALID_CONTENT_SOURCE_AUTHORIZATION));

        // when
        boolean completed = service.complete(
                ContentSourceProvider.NOTION,
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
