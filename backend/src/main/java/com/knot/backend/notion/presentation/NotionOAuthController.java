package com.knot.backend.notion.presentation;

import com.knot.backend.auth.domain.AuthenticatedMember;
import com.knot.backend.notion.application.NotionConnectionQueryService;
import com.knot.backend.notion.application.NotionOAuthAuthorizationService;
import com.knot.backend.notion.application.NotionOAuthCallbackService;
import com.knot.backend.notion.application.NotionOAuthSettings;
import com.knot.backend.notion.application.dto.result.NotionOAuthAuthorizationResult;
import com.knot.backend.notion.presentation.dto.response.NotionConnectionStatusResponse;
import com.knot.backend.notion.presentation.dto.response.NotionOAuthAuthorizationResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Notion 연결", description = "워크스페이스 Notion OAuth 연결과 상태 조회")
@RestController
public class NotionOAuthController {
    private static final String AUTHORIZATION_PATH = "/api/v1/workspaces/{workspaceId}/notion-oauth-authorizations";

    private final NotionOAuthAuthorizationService authorizationService;
    private final NotionOAuthCallbackService callbackService;
    private final NotionConnectionQueryService connectionQueryService;
    private final NotionOAuthSettings settings;

    public NotionOAuthController(
            NotionOAuthAuthorizationService authorizationService,
            NotionOAuthCallbackService callbackService,
            NotionConnectionQueryService connectionQueryService,
            NotionOAuthSettings settings
    ) {
        this.authorizationService = authorizationService;
        this.callbackService = callbackService;
        this.connectionQueryService = connectionQueryService;
        this.settings = settings;
    }

    @PostMapping(value = AUTHORIZATION_PATH, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<NotionOAuthAuthorizationResponse> start(
            @PathVariable Long workspaceId,
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember
    ) {
        NotionOAuthAuthorizationResult result = authorizationService.start(
                workspaceId,
                authenticatedMember.getMemberId()
        );
        return ResponseEntity.status(HttpStatus.CREATED)
                .cacheControl(CacheControl.noStore())
                .body(NotionOAuthAuthorizationResponse.from(result));
    }

    @GetMapping("/api/v1/notion/oauth/callback")
    public ResponseEntity<Void> callback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error
    ) {
        boolean connected = callbackService.complete(
                code,
                state,
                error
        );
        URI redirectUri = connected ? settings.successRedirectUri() : settings.failureRedirectUri();
        return ResponseEntity.status(HttpStatus.SEE_OTHER)
                .cacheControl(CacheControl.noStore())
                .location(redirectUri)
                .build();
    }

    @GetMapping(value = "/api/v1/workspaces/{workspaceId}/notion-connection", produces = MediaType.APPLICATION_JSON_VALUE)
    public NotionConnectionStatusResponse status(
            @PathVariable Long workspaceId,
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember
    ) {
        return NotionConnectionStatusResponse.from(
                connectionQueryService.findStatus(
                        workspaceId,
                        authenticatedMember.getMemberId()
                )
        );
    }
}
