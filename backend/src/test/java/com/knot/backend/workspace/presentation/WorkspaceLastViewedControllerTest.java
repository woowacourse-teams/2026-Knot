package com.knot.backend.workspace.presentation;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.knot.backend.auth.domain.AuthenticatedMember;
import com.knot.backend.global.exception.GlobalExceptionHandler;
import com.knot.backend.workspace.application.WorkspaceLastViewedService;
import com.knot.backend.workspace.domain.WorkspaceErrorCode;
import com.knot.backend.workspace.domain.WorkspaceException;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class WorkspaceLastViewedControllerTest {
    private WorkspaceLastViewedService workspaceLastViewedService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        workspaceLastViewedService = mock(WorkspaceLastViewedService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new WorkspaceLastViewedController(workspaceLastViewedService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("인증된 member가 마지막으로 본 워크스페이스를 갱신하면 204를 반환한다")
    void update_success() throws Exception {
        // given
        SecurityContextHolder.getContext()
                .setAuthentication(memberAuthentication());

        // when
        ResultActions result = mockMvc.perform(
                put("/api/v1/members/me/last-viewed-workspace").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"workspaceId":12}
                                """)
        );

        // then
        result.andExpect(status().isNoContent())
                .andExpect(content().string(""));
        verify(workspaceLastViewedService).update(
                1L,
                12L
        );
        verifyNoMoreInteractions(workspaceLastViewedService);
    }

    @Test
    @DisplayName("workspaceId가 없으면 VALIDATION_ERROR 400 응답을 반환한다")
    void update_failure_missingWorkspaceId() throws Exception {
        // given
        SecurityContextHolder.getContext()
                .setAuthentication(memberAuthentication());

        // when
        ResultActions result = mockMvc.perform(
                put("/api/v1/members/me/last-viewed-workspace").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {}
                                """)
        );

        // then
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("workspaceId"))
                .andExpect(jsonPath("$.fieldErrors[0].reason").value("워크스페이스 ID는 필수입니다"));
        verifyNoInteractions(workspaceLastViewedService);
    }

    @Test
    @DisplayName("workspaceId가 양수가 아니면 VALIDATION_ERROR 400 응답을 반환한다")
    void update_failure_nonPositiveWorkspaceId() throws Exception {
        // given
        SecurityContextHolder.getContext()
                .setAuthentication(memberAuthentication());

        // when
        ResultActions result = mockMvc.perform(
                put("/api/v1/members/me/last-viewed-workspace").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"workspaceId":0}
                                """)
        );

        // then
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("workspaceId"))
                .andExpect(jsonPath("$.fieldErrors[0].reason").value("워크스페이스 ID는 양수여야 합니다"));
        verifyNoInteractions(workspaceLastViewedService);
    }

    @Test
    @DisplayName("워크스페이스가 없거나 멤버가 아니면 WORKSPACE_NOT_FOUND 404 응답을 반환한다")
    void update_failure_workspaceNotFound() throws Exception {
        // given
        SecurityContextHolder.getContext()
                .setAuthentication(memberAuthentication());
        doThrow(new WorkspaceException(WorkspaceErrorCode.WORKSPACE_NOT_FOUND)).when(workspaceLastViewedService)
                .update(
                        1L,
                        12L
                );

        // when
        ResultActions result = mockMvc.perform(
                put("/api/v1/members/me/last-viewed-workspace").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"workspaceId":12}
                                """)
        );

        // then
        result.andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("WORKSPACE_NOT_FOUND"));
        verify(workspaceLastViewedService).update(
                1L,
                12L
        );
    }

    private UsernamePasswordAuthenticationToken memberAuthentication() {
        return new UsernamePasswordAuthenticationToken(
                AuthenticatedMember.of(
                        1L,
                        "octocat",
                        null
                ),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
    }
}
