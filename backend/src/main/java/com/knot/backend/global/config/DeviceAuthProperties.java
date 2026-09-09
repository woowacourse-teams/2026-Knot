package com.knot.backend.global.config;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 데스크톱 2단계 인증 설정(기획서 5.2). 코드 TTL 120초, 리프레시 토큰 미사용 만료 6개월 */
@Getter
@Setter
@ConfigurationProperties(prefix = "auth.device")
public class DeviceAuthProperties {
    private Duration codeExpiration = Duration.ofSeconds(120);
    private Duration refreshTokenExpiration = Duration.ofDays(180);
}
