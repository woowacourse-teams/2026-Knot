package com.knot.backend.workspace.infrastructure.notion.collector;

import com.knot.backend.workspace.application.NotionContentCollector;
import com.knot.backend.workspace.infrastructure.notion.oauth.NotionOAuthProperties;
import io.micrometer.core.instrument.MeterRegistry;
import java.net.URI;
import java.net.http.HttpClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "notion.oauth", name = "enabled", havingValue = "true")
public class NotionCollectorConfig {

    @Bean
    public NotionContentCollector notionContentCollector(
            @Qualifier("notionOAuthHttpClient") HttpClient httpClient,
            NotionOAuthProperties properties,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry
    ) {
        return new HttpNotionContentCollector(
                httpClient,
                objectMapper,
                URI.create("https://api.notion.com/v1"),
                properties.requestTimeout(),
                new MicrometerNotionCollectionObserver(meterRegistry),
                Thread::sleep
        );
    }
}
