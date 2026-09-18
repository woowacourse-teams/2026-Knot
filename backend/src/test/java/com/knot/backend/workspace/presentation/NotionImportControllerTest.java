package com.knot.backend.workspace.presentation;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.knot.backend.auth.domain.AuthenticatedMember;
import com.knot.backend.global.exception.GlobalExceptionHandler;
import com.knot.backend.workspace.application.ContentImportQueryService;
import com.knot.backend.workspace.application.dto.result.ContentImportStatusResult;
import com.knot.backend.workspace.domain.ContentImportErrorCode;
import com.knot.backend.workspace.domain.ContentImportException;
import com.knot.backend.workspace.domain.ContentImportStatus;
import java.time.Instant;
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

class NotionImportControllerTest {
    private static final Instant CREATED_AT = Instant.parse("2026-08-31T00:00:00Z");

    private ContentImportQueryService importQueryService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        importQueryService = mock(ContentImportQueryService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new NotionImportController(importQueryService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
        SecurityContextHolder.getContext()
                .setAuthentication(memberAuthentication());
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @DisplayName("Workspace 멤버가 Import 상태를 조회하면 no-store와 상태 응답을 반환한다")
    @Test
    void status_success() throws Exception {
        // given
        when(
                importQueryService.findStatus(
                        1L,
                        10L
                )
        ).thenReturn(
                new ContentImportStatusResult(
                        1L,
                        ContentImportStatus.RUNNING,
                        10,
                        4,
                        CREATED_AT,
                        CREATED_AT.plusSeconds(1),
                        null
                )
        );

        // when
        ResultActions result = mockMvc.perform(
                get(
                        "/api/v1/imports/{importRunId}",
                        1L
                )
        );

        // then
        result.andExpect(status().isOk())
                .andExpect(
                        header().string(
                                HttpHeaders.CACHE_CONTROL,
                                "no-store"
                        )
                )
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.status").value("RUNNING"))
                .andExpect(jsonPath("$.totalPageCount").value(10))
                .andExpect(jsonPath("$.processedPageCount").value(4))
                .andExpect(jsonPath("$.failureReason").isEmpty())
                .andExpect(jsonPath("$.createdAt").value("2026-08-31T00:00:00Z"))
                .andExpect(jsonPath("$.startedAt").value("2026-08-31T00:00:01Z"))
                .andExpect(jsonPath("$.completedAt").isEmpty());
        verify(importQueryService).findStatus(
                1L,
                10L
        );
    }

    @DisplayName("실패한 Import 상태는 기존 Notion 공개 실패 사유를 반환한다")
    @Test
    void status_success_failedReasonCompatibility() throws Exception {
        // given
        when(
                importQueryService.findStatus(
                        1L,
                        10L
                )
        ).thenReturn(
                new ContentImportStatusResult(
                        1L,
                        ContentImportStatus.FAILED,
                        10,
                        4,
                        CREATED_AT,
                        CREATED_AT.plusSeconds(1),
                        CREATED_AT.plusSeconds(2)
                )
        );

        // when
        ResultActions result = mockMvc.perform(
                get(
                        "/api/v1/imports/{importRunId}",
                        1L
                )
        );

        // then
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.failureReason").value("Notion 문서를 가져오지 못했습니다"));
    }

    @DisplayName("Import Run ID 형식이 잘못되면 INVALID_PARAMETER 400을 반환한다")
    @Test
    void status_failure_invalidImportRunIdType() throws Exception {
        // given
        String importRunId = "not-a-number";

        // when
        ResultActions result = mockMvc.perform(
                get(
                        "/api/v1/imports/{importRunId}",
                        importRunId
                )
        );

        // then
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("importRunId"));
        verifyNoInteractions(importQueryService);
    }

    @DisplayName("조회할 수 없는 Import Run은 NOTION_IMPORT_RUN_NOT_FOUND 404를 반환한다")
    @Test
    void status_failure_notFound() throws Exception {
        // given
        when(
                importQueryService.findStatus(
                        1L,
                        10L
                )
        ).thenThrow(new ContentImportException(ContentImportErrorCode.CONTENT_IMPORT_RUN_NOT_FOUND));

        // when
        ResultActions result = mockMvc.perform(
                get(
                        "/api/v1/imports/{importRunId}",
                        1L
                )
        );

        // then
        result.andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOTION_IMPORT_RUN_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Notion Import 실행을 찾을 수 없습니다"));
    }

    private UsernamePasswordAuthenticationToken memberAuthentication() {
        return new UsernamePasswordAuthenticationToken(
                AuthenticatedMember.of(
                        10L,
                        "hyunsung",
                        null
                ),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
    }
}
