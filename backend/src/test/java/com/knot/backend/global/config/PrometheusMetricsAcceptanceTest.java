package com.knot.backend.global.config;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.knot.backend.testsupport.TestApplicationProperties;
import com.knot.backend.auth.domain.AuthTokenProvider;
import com.knot.backend.auth.domain.AuthenticatedMember;
import jakarta.servlet.http.Cookie;
import com.knot.backend.testsupport.TestcontainersConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;

@Tag("acceptance")
@ActiveProfiles({"dev", "observability"})
@Import(TestcontainersConfiguration.class)
@TestApplicationProperties
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureMetrics
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class PrometheusMetricsAcceptanceTest {
    private final MockMvc mockMvc;
    private final AuthTokenProvider authTokenProvider;
    private final JwtProperties jwtProperties;

    PrometheusMetricsAcceptanceTest(
            MockMvc mockMvc,
            AuthTokenProvider authTokenProvider,
            JwtProperties jwtProperties
    ) {
        this.mockMvc = mockMvc;
        this.authTokenProvider = authTokenProvider;
        this.jwtProperties = jwtProperties;
    }

    @Test
    @DisplayName("로컬 수집기는 인증 쿠키 없이 JVM과 DB 풀 지표를 조회한다")
    void prometheus_success_loopbackScrape() throws Exception {
        // when & then
        mockMvc.perform(get("/actuator/prometheus").with(request -> {
            request.setRemoteAddr("127.0.0.1");
            return request;
        }))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("jvm_memory_used_bytes")))
                .andExpect(content().string(containsString("hikaricp_connections")));
    }

    @Test
    @DisplayName("외부 요청은 로컬 IP를 Forwarded 헤더에 넣어도 지표에 접근하지 못한다")
    void prometheus_failure_externalAddressWithSpoofedForwarding() throws Exception {
        // when & then
        mockMvc.perform(
                get("/actuator/prometheus").header(
                        "X-Forwarded-For",
                        "127.0.0.1"
                )
                        .header(
                                "Forwarded",
                                "for=127.0.0.1"
                        )
                        .with(request -> {
                            request.setRemoteAddr("203.0.113.10");
                            return request;
                        })
        )
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("IPv6 loopback 수집기도 지표를 조회한다")
    void prometheus_success_ipv6LoopbackScrape() throws Exception {
        // when & then
        mockMvc.perform(get("/actuator/prometheus").with(request -> {
            request.setRemoteAddr("::1");
            return request;
        }))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("지표 수집을 활성화해도 기존 공개 health는 유지한다")
    void health_success_publicHealthUnchanged() throws Exception {
        // when & then
        mockMvc.perform(get("/actuator/health").with(request -> {
            request.setRemoteAddr("203.0.113.10");
            return request;
        }))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("인증된 외부 사용자도 운영 지표를 조회하지 못한다")
    void prometheus_failure_authenticatedExternalUser() throws Exception {
        // given
        String token = authTokenProvider.issue(
                AuthenticatedMember.of(
                        1L,
                        "metrics-client",
                        null
                )
        );
        Cookie cookie = new Cookie(
                jwtProperties.getCookieName(),
                token
        );
        mockMvc.perform(get("/api/v1/auth/me").cookie(cookie))
                .andExpect(status().isOk());

        // when & then
        mockMvc.perform(
                get("/actuator/prometheus").cookie(cookie)
                        .with(request -> {
                            request.setRemoteAddr("203.0.113.10");
                            return request;
                        })
        )
                .andExpect(status().isForbidden());
    }
}
