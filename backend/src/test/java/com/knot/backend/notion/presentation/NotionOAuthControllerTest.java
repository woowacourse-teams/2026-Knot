package com.knot.backend.notion.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.knot.backend.auth.domain.AuthenticatedMember;
import com.knot.backend.notion.application.NotionConnectionQueryService;
import com.knot.backend.notion.application.NotionOAuthAuthorizationService;
import com.knot.backend.notion.application.NotionOAuthCallbackService;
import com.knot.backend.notion.application.NotionOAuthSettings;
import com.knot.backend.notion.application.dto.result.NotionConnectionStatusResult;
import com.knot.backend.notion.application.dto.result.NotionOAuthAuthorizationResult;
import com.knot.backend.notion.domain.NotionConnectionStatus;
import com.knot.backend.notion.presentation.dto.response.NotionConnectionStatusResponse;
import com.knot.backend.notion.presentation.dto.response.NotionOAuthAuthorizationResponse;
import java.net.URI;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

class NotionOAuthControllerTest {
    private static final Long WORKSPACE_ID = 1L;
    private static final long MEMBER_ID = 2L;
    private static final URI AUTHORIZATION_URI = URI.create("https://api.notion.test/oauth?state=raw-state");
    private static final URI SUCCESS_REDIRECT_URI = URI.create("https://app.example.com/notion?result=connected");
    private static final URI FAILURE_REDIRECT_URI = URI.create("https://app.example.com/notion?result=failed");

    private final NotionOAuthAuthorizationService authorizationService = mock(NotionOAuthAuthorizationService.class);
    private final NotionOAuthCallbackService callbackService = mock(NotionOAuthCallbackService.class);
    private final NotionConnectionQueryService connectionQueryService = mock(NotionConnectionQueryService.class);
    private final NotionOAuthSettings settings = mock(NotionOAuthSettings.class);
    private final NotionOAuthController controller = new NotionOAuthController(
            authorizationService,
            callbackService,
            connectionQueryService,
            settings
    );
    private final AuthenticatedMember authenticatedMember = AuthenticatedMember.of(
            MEMBER_ID,
            "현성",
            null
    );

    @DisplayName("Notion OAuth 시작은 201, authorization URL, no-store를 반환한다")
    @Test
    void start_success_returnsCreatedAuthorizationUrl() {
        // given
        when(
                authorizationService.start(
                        WORKSPACE_ID,
                        MEMBER_ID
                )
        ).thenReturn(new NotionOAuthAuthorizationResult(AUTHORIZATION_URI));

        // when
        ResponseEntity<NotionOAuthAuthorizationResponse> response = controller.start(
                WORKSPACE_ID,
                authenticatedMember
        );

        // then
        assertThat(
                response.getStatusCode()
                        .value()
        ).isEqualTo(201);
        assertThat(response.getBody()).isEqualTo(new NotionOAuthAuthorizationResponse(AUTHORIZATION_URI.toString()));
        assertThat(
                response.getHeaders()
                        .getFirst(HttpHeaders.CACHE_CONTROL)
        ).isEqualTo("no-store");
        verify(authorizationService).start(
                WORKSPACE_ID,
                MEMBER_ID
        );
    }

    @DisplayName("Notion OAuth callback 성공은 성공 화면으로 303 redirect하고 no-store를 반환한다")
    @Test
    void callback_success_redirectsSuccessUri() {
        // given
        when(
                callbackService.complete(
                        "oauth-code",
                        "oauth-state",
                        null
                )
        ).thenReturn(true);
        when(settings.successRedirectUri()).thenReturn(SUCCESS_REDIRECT_URI);

        // when
        ResponseEntity<Void> response = controller.callback(
                "oauth-code",
                "oauth-state",
                null
        );

        // then
        assertThat(
                response.getStatusCode()
                        .value()
        ).isEqualTo(303);
        assertThat(
                response.getHeaders()
                        .getLocation()
        ).isEqualTo(SUCCESS_REDIRECT_URI);
        assertThat(
                response.getHeaders()
                        .getFirst(HttpHeaders.CACHE_CONTROL)
        ).isEqualTo("no-store");
    }

    @DisplayName("Notion OAuth callback 실패는 실패 화면으로 303 redirect하고 no-store를 반환한다")
    @Test
    void callback_failure_redirectsFailureUri() {
        // given
        when(
                callbackService.complete(
                        null,
                        "oauth-state",
                        "access_denied"
                )
        ).thenReturn(false);
        when(settings.failureRedirectUri()).thenReturn(FAILURE_REDIRECT_URI);

        // when
        ResponseEntity<Void> response = controller.callback(
                null,
                "oauth-state",
                "access_denied"
        );

        // then
        assertThat(
                response.getStatusCode()
                        .value()
        ).isEqualTo(303);
        assertThat(
                response.getHeaders()
                        .getLocation()
        ).isEqualTo(FAILURE_REDIRECT_URI);
        assertThat(
                response.getHeaders()
                        .getFirst(HttpHeaders.CACHE_CONTROL)
        ).isEqualTo("no-store");
    }

    @DisplayName("Notion connection 상태 조회는 service 결과를 응답 DTO로 반환한다")
    @Test
    void status_success_returnsConnectionStatus() {
        // given
        when(
                connectionQueryService.findStatus(
                        WORKSPACE_ID,
                        MEMBER_ID
                )
        ).thenReturn(new NotionConnectionStatusResult(NotionConnectionStatus.CONNECTED));

        // when
        NotionConnectionStatusResponse response = controller.status(
                WORKSPACE_ID,
                authenticatedMember
        );

        // then
        assertThat(response).isEqualTo(new NotionConnectionStatusResponse(NotionConnectionStatus.CONNECTED));
    }
}
