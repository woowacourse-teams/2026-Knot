package com.knot.backend.global.config;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "auth.jwt")
public class JwtProperties {

    private String secret;
    private Duration expiration = Duration.ofHours(1);
    private String cookieName = "__Host-KNOT_ACCESS_TOKEN";
    private boolean secure = true;
}
