package com.knot.backend.global.config;

import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "auth.cors")
public class CorsProperties {
    private List<String> allowedOrigins = List.of("https://knoted.kr");
}
