package com.knot.backend.workspace.presentation;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.knot.backend.auth.domain.AuthenticatedMember;
import com.knot.backend.global.exception.GlobalExceptionHandler;
import com.knot.backend.workspace.application.WorkspaceService;
import com.knot.backend.workspace.application.dto.result.WorkspaceCreateResult;
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

class WorkspaceControllerTest {
    private WorkspaceService workspaceService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        workspaceService = mock(WorkspaceService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new WorkspaceController(workspaceService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("인증된 member가 워크스페이스를 생성하면 201과 생성된 ID를 반환한다")
    void create_success() throws Exception {
        // given
        SecurityContextHolder.getContext()
                .setAuthentication(memberAuthentication());
        when(
                workspaceService.create(
                        1L,
                        "Knot 팀"
                )
        ).thenReturn(new WorkspaceCreateResult(7L));

        // when
        ResultActions result = mockMvc.perform(
                post("/api/v1/workspaces").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Knot 팀"}
                                """)
        );

        // then
        result.andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(7L));
        verify(workspaceService).create(
                1L,
                "Knot 팀"
        );
    }

    @Test
    @DisplayName("워크스페이스 이름 규칙을 위반하면 INVALID_WORKSPACE_NAME 400 응답을 반환한다")
    void create_failure_invalidWorkspaceName() throws Exception {
        // given
        SecurityContextHolder.getContext()
                .setAuthentication(memberAuthentication());
        when(
                workspaceService.create(
                        1L,
                        "Knot!"
                )
        ).thenThrow(new WorkspaceException(WorkspaceErrorCode.INVALID_WORKSPACE_NAME));

        // when
        ResultActions result = mockMvc.perform(
                post("/api/v1/workspaces").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Knot!"}
                                """)
        );

        // then
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_WORKSPACE_NAME"))
                .andExpect(jsonPath("$.message").value("워크스페이스 이름이 올바르지 않습니다"));
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
