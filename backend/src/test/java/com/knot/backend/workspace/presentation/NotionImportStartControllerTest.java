package com.knot.backend.workspace.presentation;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.knot.backend.auth.domain.AuthenticatedMember;
import com.knot.backend.global.exception.GlobalExceptionHandler;
import com.knot.backend.workspace.application.ContentImportCommandService;
import com.knot.backend.workspace.application.dto.result.ContentImportRunRequestResult;
import com.knot.backend.workspace.domain.ContentImportErrorCode;
import com.knot.backend.workspace.domain.ContentImportException;
import com.knot.backend.workspace.domain.ContentSourceProvider;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class NotionImportStartControllerTest {
    private static final Long WORKSPACE_ID = 1L;
    private static final long MEMBER_ID = 2L;
    private static final long IMPORT_RUN_ID = 3L;

    private ContentImportCommandService importCommandService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        importCommandService = mock(ContentImportCommandService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new NotionImportStartController(importCommandService))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        SecurityContextHolder.getContext()
                .setAuthentication(memberAuthentication());
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @DisplayName("새 PENDING Run을 만들면 202와 상태 조회 Location을 반환한다")
    @Test
    void start_success_created() throws Exception {
        // given
        when(
                importCommandService.start(
                        WORKSPACE_ID,
                        MEMBER_ID,
                        ContentSourceProvider.NOTION
                )
        ).thenReturn(
                new ContentImportRunRequestResult(
                        IMPORT_RUN_ID,
                        true
                )
        );

        // when
        ResultActions result = mockMvc.perform(
                post(
                        "/api/v1/workspaces/{workspaceId}/imports",
                        WORKSPACE_ID
                )
        );

        // then
        result.andExpect(status().isAccepted())
                .andExpect(
                        header().string(
                                HttpHeaders.LOCATION,
                                "/api/v1/imports/3"
                        )
                )
                .andExpect(jsonPath("$.id").value(IMPORT_RUN_ID));
        verify(importCommandService).start(
                WORKSPACE_ID,
                MEMBER_ID,
                ContentSourceProvider.NOTION
        );
    }

    @DisplayName("활성 Run이 있으면 같은 ID와 Location을 포함한 409를 반환한다")
    @Test
    void start_success_existingActiveRun() throws Exception {
        // given
        when(
                importCommandService.start(
                        WORKSPACE_ID,
                        MEMBER_ID,
                        ContentSourceProvider.NOTION
                )
        ).thenReturn(
                new ContentImportRunRequestResult(
                        IMPORT_RUN_ID,
                        false
                )
        );

        // when
        ResultActions result = mockMvc.perform(
                post(
                        "/api/v1/workspaces/{workspaceId}/imports",
                        WORKSPACE_ID
                )
        );

        // then
        result.andExpect(status().isConflict())
                .andExpect(
                        header().string(
                                HttpHeaders.LOCATION,
                                "/api/v1/imports/3"
                        )
                )
                .andExpect(jsonPath("$.id").value(IMPORT_RUN_ID));
    }

    @DisplayName("Content Source 연결이 없으면 기존 Notion 오류 계약으로 변환한다")
    @Test
    void start_failure_mapsConnectionNotConnected() throws Exception {
        // given
        when(
                importCommandService.start(
                        WORKSPACE_ID,
                        MEMBER_ID,
                        ContentSourceProvider.NOTION
                )
        ).thenThrow(new ContentImportException(ContentImportErrorCode.CONTENT_SOURCE_CONNECTION_NOT_CONNECTED));

        // when
        ResultActions result = mockMvc.perform(
                post(
                        "/api/v1/workspaces/{workspaceId}/imports",
                        WORKSPACE_ID
                )
        );

        // then
        result.andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOTION_CONNECTION_NOT_CONNECTED"))
                .andExpect(jsonPath("$.message").value("Notion 연결이 필요합니다"));
    }

    @DisplayName("Content Source 재인증이 필요하면 기존 Notion 오류 계약으로 변환한다")
    @Test
    void start_failure_mapsConnectionReauthenticationRequired() throws Exception {
        // given
        when(
                importCommandService.start(
                        WORKSPACE_ID,
                        MEMBER_ID,
                        ContentSourceProvider.NOTION
                )
        ).thenThrow(
                new ContentImportException(ContentImportErrorCode.CONTENT_SOURCE_CONNECTION_REAUTHENTICATION_REQUIRED)
        );

        // when
        ResultActions result = mockMvc.perform(
                post(
                        "/api/v1/workspaces/{workspaceId}/imports",
                        WORKSPACE_ID
                )
        );

        // then
        result.andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOTION_CONNECTION_REAUTHENTICATION_REQUIRED"))
                .andExpect(jsonPath("$.message").value("Notion 연결 재인증이 필요합니다"));
    }

    private UsernamePasswordAuthenticationToken memberAuthentication() {
        return new UsernamePasswordAuthenticationToken(
                AuthenticatedMember.of(
                        MEMBER_ID,
                        "hyunsung",
                        null
                ),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
    }
}
