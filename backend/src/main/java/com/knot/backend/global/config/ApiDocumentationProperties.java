package com.knot.backend.global.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "knot.api-docs")
public class ApiDocumentationProperties {
    private boolean enabled;
}
