package com.knot.backend.mcp.presentation;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.knot.backend.auth.domain.AuthTokenProvider;
import com.knot.backend.auth.domain.AuthenticatedMember;
import com.knot.backend.testsupport.TestApplicationProperties;
import com.knot.backend.testsupport.TestcontainersConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 원격 MCP 서버는 기본으로 꺼져 있다(기획서 6.6 기본값 행, {@code mcp.remote.enabled=false}). 켜지 않은 서버에서는
 * 인증된 요청도 엔드포인트를 찾지 못한다 — 이 테스트가 기본값이 바뀌는 것을 막는다.
 */
@Tag("acceptance")
@Import(TestcontainersConfiguration.class)
@TestApplicationProperties
@SpringBootTest
@AutoConfigureMockMvc
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class McpDisabledAcceptanceTest {
    private final MockMvc mockMvc;
    private final AuthTokenProvider authTokenProvider;
    private final JdbcClient jdbcClient;

    McpDisabledAcceptanceTest(
            MockMvc mockMvc,
            AuthTokenProvider authTokenProvider,
            JdbcClient jdbcClient
    ) {
        this.mockMvc = mockMvc;
        this.authTokenProvider = authTokenProvider;
        this.jdbcClient = jdbcClient;
    }

    @Test
    @DisplayName("기본 설정에서는 인증된 요청에도 MCP 엔드포인트가 없다")
    void post_failure_disabledByDefault() throws Exception {
        // given
        long memberId = jdbcClient.sql("""
                INSERT INTO members (nickname, profile_image_url)
                VALUES ('mcp-disabled', NULL)
                RETURNING id
                """)
                .query(Long.class)
                .single();

        // when
        ResultActions result = mockMvc.perform(
                post("/mcp").header(
                        HttpHeaders.AUTHORIZATION,
                        "Bearer " + authTokenProvider.issue(
                                AuthenticatedMember.of(
                                        memberId,
                                        "mcp-disabled",
                                        null
                                )
                        )
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"jsonrpc":"2.0","id":1,"method":"ping"}
                                """)
        );

        // then
        result.andExpect(status().isNotFound());
    }
}
