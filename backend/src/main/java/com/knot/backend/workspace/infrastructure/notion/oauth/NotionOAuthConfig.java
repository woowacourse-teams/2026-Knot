package com.knot.backend.workspace.infrastructure.notion.oauth;

import com.knot.backend.workspace.application.NotionOAuthClient;
import com.knot.backend.workspace.application.NotionOAuthSecretProtector;
import com.knot.backend.workspace.application.NotionOAuthStateGenerator;
import com.knot.backend.workspace.domain.NotionErrorCode;
import com.knot.backend.workspace.domain.NotionException;
import com.knot.backend.workspace.infrastructure.notion.security.AesGcmNotionOAuthSecretProtector;
import com.knot.backend.workspace.infrastructure.notion.security.SecureNotionOAuthStateGenerator;
import java.net.http.HttpClient;
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
            throw new NotionException(NotionErrorCode.NOTION_OAUTH_CONFIGURATION_INVALID);
        }
        return HttpClient.newBuilder()
                .connectTimeout(requestTimeout)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @Bean
    public NotionOAuthClient notionOAuthClient(
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
    public NotionOAuthStateGenerator notionOAuthStateGenerator() {
        return new SecureNotionOAuthStateGenerator();
    }

    @Bean
    public NotionOAuthSecretProtector notionOAuthSecretProtector(NotionOAuthProperties properties) {
        return new AesGcmNotionOAuthSecretProtector(properties);
    }
}
