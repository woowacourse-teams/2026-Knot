package com.knot.backend.workspace.infrastructure.notion.oauth;

import com.knot.backend.workspace.application.ContentSourceAuthorizationClient;
import com.knot.backend.workspace.application.ContentSourceAuthorizationService;
import com.knot.backend.workspace.application.ContentSourceCallbackService;
import com.knot.backend.workspace.application.ContentSourceConnectionQueryService;
import com.knot.backend.workspace.application.ContentSourceConnectionService;
import com.knot.backend.workspace.application.ContentSourceSecretProtector;
import com.knot.backend.workspace.application.ContentSourceStateGenerator;
import com.knot.backend.workspace.domain.ContentSourceAuthorizationRepository;
import com.knot.backend.workspace.domain.ContentSourceConnectionRepository;
import com.knot.backend.workspace.domain.ContentSourceErrorCode;
import com.knot.backend.workspace.domain.ContentSourceException;
import com.knot.backend.workspace.domain.WorkspaceMemberRepository;
import com.knot.backend.workspace.domain.WorkspaceRepository;
import com.knot.backend.workspace.infrastructure.notion.security.AesGcmNotionOAuthSecretProtector;
import com.knot.backend.workspace.infrastructure.notion.security.SecureNotionOAuthStateGenerator;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "notion.oauth", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(NotionOAuthProperties.class)
public class NotionOAuthConfig {

    @Bean
    public HttpClient notionOAuthHttpClient(NotionOAuthProperties properties) {
        Duration requestTimeout = properties.requestTimeout();
        if (requestTimeout == null || requestTimeout.isZero() || requestTimeout.isNegative()) {
            throw new ContentSourceException(ContentSourceErrorCode.CONTENT_SOURCE_CONFIGURATION_INVALID);
        }
        return HttpClient.newBuilder()
                .connectTimeout(requestTimeout)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @Bean
    public ContentSourceAuthorizationClient notionOAuthClient(
            NotionOAuthProperties properties,
            HttpClient notionOAuthHttpClient,
            tools.jackson.databind.ObjectMapper objectMapper
    ) {
        return new HttpNotionOAuthClient(
                properties,
                notionOAuthHttpClient,
                objectMapper
        );
    }

    @Bean
    public ContentSourceStateGenerator notionOAuthStateGenerator() {
        return new SecureNotionOAuthStateGenerator();
    }

    @Bean
    public ContentSourceSecretProtector notionOAuthSecretProtector(NotionOAuthProperties properties) {
        return new AesGcmNotionOAuthSecretProtector(properties);
    }

    @Bean
    public ContentSourceAuthorizationService contentSourceAuthorizationService(
            WorkspaceRepository workspaceRepository,
            WorkspaceMemberRepository workspaceMemberRepository,
            ContentSourceAuthorizationRepository authorizationRepository,
            ContentSourceStateGenerator stateGenerator,
            ContentSourceSecretProtector secretProtector,
            ContentSourceAuthorizationClient authorizationClient,
            NotionOAuthProperties settings,
            Clock clock
    ) {
        return new ContentSourceAuthorizationService(
                workspaceRepository,
                workspaceMemberRepository,
                authorizationRepository,
                stateGenerator,
                secretProtector,
                authorizationClient,
                settings,
                clock
        );
    }

    @Bean
    public ContentSourceConnectionService contentSourceConnectionService(
            WorkspaceRepository workspaceRepository,
            WorkspaceMemberRepository workspaceMemberRepository,
            ContentSourceAuthorizationRepository authorizationRepository,
            ContentSourceConnectionRepository connectionRepository,
            ContentSourceSecretProtector secretProtector,
            Clock clock
    ) {
        return new ContentSourceConnectionService(
                workspaceRepository,
                workspaceMemberRepository,
                authorizationRepository,
                connectionRepository,
                secretProtector,
                clock
        );
    }

    @Bean
    public ContentSourceCallbackService contentSourceCallbackService(
            ContentSourceAuthorizationService authorizationService,
            ContentSourceAuthorizationClient authorizationClient,
            ContentSourceConnectionService connectionService
    ) {
        return new ContentSourceCallbackService(
                authorizationService,
                authorizationClient,
                connectionService
        );
    }

    @Bean
    public ContentSourceConnectionQueryService contentSourceConnectionQueryService(
            WorkspaceRepository workspaceRepository,
            WorkspaceMemberRepository workspaceMemberRepository,
            ContentSourceConnectionRepository connectionRepository
    ) {
        return new ContentSourceConnectionQueryService(
                workspaceRepository,
                workspaceMemberRepository,
                connectionRepository
        );
    }
}
