package com.knot.backend.workspace.presentation;

import com.knot.backend.auth.domain.AuthenticatedMember;
import com.knot.backend.workspace.application.ContentSourceConnectionQueryService;
import com.knot.backend.workspace.application.ContentSourceAuthorizationService;
import com.knot.backend.workspace.application.ContentSourceCallbackService;
import com.knot.backend.workspace.application.ContentSourceAuthorizationSettings;
import com.knot.backend.workspace.application.dto.result.ContentSourceAuthorizationResult;
import com.knot.backend.workspace.application.dto.result.ContentSourceCallbackResult;
import com.knot.backend.workspace.domain.ContentSourceProvider;
import com.knot.backend.workspace.presentation.dto.response.NotionConnectionStatusResponse;
import com.knot.backend.workspace.presentation.dto.response.NotionOAuthAuthorizationResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
@ConditionalOnProperty(prefix = "notion.oauth", name = "enabled", havingValue = "true")
public class NotionOAuthController {
    private static final String AUTHORIZATION_PATH = "/api/v1/workspaces/{workspaceId}/notion-oauth-authorizations";
    private static final String CONNECTION_PATH = "/api/v1/workspaces/{workspaceId}/notion-connection";

    private final ContentSourceAuthorizationService authorizationService;
    private final ContentSourceCallbackService callbackService;
    private final ContentSourceConnectionQueryService connectionQueryService;
    private final ContentSourceAuthorizationSettings settings;

    public NotionOAuthController(
            ContentSourceAuthorizationService authorizationService,
            ContentSourceCallbackService callbackService,
            ContentSourceConnectionQueryService connectionQueryService,
            ContentSourceAuthorizationSettings settings
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
        ContentSourceAuthorizationResult result = authorizationService.start(
                workspaceId,
                authenticatedMember.getMemberId(),
                ContentSourceProvider.NOTION
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
        ContentSourceCallbackResult result = callbackService.complete(
                ContentSourceProvider.NOTION,
                code,
                state,
                error
        );
        URI redirectUri = result.connected()
                ? settings.successRedirectUri(result.workspaceId())
                : settings.failureRedirectUri(result.workspaceId());
        return ResponseEntity.status(HttpStatus.SEE_OTHER)
                .cacheControl(CacheControl.noStore())
                .location(redirectUri)
                .build();
    }

    @GetMapping(value = CONNECTION_PATH, produces = MediaType.APPLICATION_JSON_VALUE)
    public NotionConnectionStatusResponse status(
            @PathVariable Long workspaceId,
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember
    ) {
        return NotionConnectionStatusResponse.from(
                connectionQueryService.findStatus(
                        workspaceId,
                        authenticatedMember.getMemberId(),
                        ContentSourceProvider.NOTION
                )
        );
    }
}
