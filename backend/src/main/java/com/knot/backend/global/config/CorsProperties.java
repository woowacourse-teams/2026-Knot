package com.knot.backend.global.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "auth.cors")
public class CorsProperties {
    private String allowedOrigin = "https://knoted.kr";
}
