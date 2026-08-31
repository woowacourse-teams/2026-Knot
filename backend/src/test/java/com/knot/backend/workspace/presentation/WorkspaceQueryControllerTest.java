package com.knot.backend.workspace.presentation;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.knot.backend.auth.domain.AuthenticatedMember;
import com.knot.backend.global.exception.GlobalExceptionHandler;
import com.knot.backend.workspace.application.WorkspaceQueryService;
import com.knot.backend.workspace.application.dto.result.WorkspaceDetailResult;
import com.knot.backend.workspace.application.dto.result.WorkspaceListItemResult;
import com.knot.backend.workspace.application.dto.result.WorkspaceListResult;
import com.knot.backend.workspace.domain.WorkspaceErrorCode;
import com.knot.backend.workspace.domain.WorkspaceException;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class WorkspaceQueryControllerTest {
    private WorkspaceQueryService workspaceQueryService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        workspaceQueryService = mock(WorkspaceQueryService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new WorkspaceQueryController(workspaceQueryService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("워크스페이스 멤버가 단건 조회하면 200과 이름을 반환한다")
    void detail_success() throws Exception {
        // given
        SecurityContextHolder.getContext()
                .setAuthentication(memberAuthentication());
        when(
                workspaceQueryService.findDetail(
                        1L,
                        10L
                )
        ).thenReturn(new WorkspaceDetailResult("Knot 팀"));

        // when
        ResultActions result = mockMvc.perform(
                get(
                        "/workspaces/{workspaceId}",
                        1L
                )
        );

        // then
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Knot 팀"));
        verify(workspaceQueryService).findDetail(
                1L,
                10L
        );
    }

    @Test
    @DisplayName("워크스페이스 ID 형식이 잘못되면 INVALID_PARAMETER 400 응답을 반환한다")
    void detail_failure_invalidWorkspaceIdType() throws Exception {
        // given
        SecurityContextHolder.getContext()
                .setAuthentication(memberAuthentication());

        // when
        ResultActions result = mockMvc.perform(get("/workspaces/not-a-number"));

        // then
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("workspaceId"));
        verifyNoInteractions(workspaceQueryService);
    }

    @Test
    @DisplayName("워크스페이스 멤버가 아니면 WORKSPACE_ACCESS_DENIED 403 응답을 반환한다")
    void detail_failure_accessDenied() throws Exception {
        // given
        SecurityContextHolder.getContext()
                .setAuthentication(memberAuthentication());
        when(
                workspaceQueryService.findDetail(
                        1L,
                        10L
                )
        ).thenThrow(new WorkspaceException(WorkspaceErrorCode.WORKSPACE_ACCESS_DENIED));

        // when
        ResultActions result = mockMvc.perform(
                get(
                        "/workspaces/{workspaceId}",
                        1L
                )
        );

        // then
        result.andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("WORKSPACE_ACCESS_DENIED"));
    }

    @Test
    @DisplayName("워크스페이스가 없으면 WORKSPACE_NOT_FOUND 404 응답을 반환한다")
    void detail_failure_workspaceNotFound() throws Exception {
        // given
        SecurityContextHolder.getContext()
                .setAuthentication(memberAuthentication());
        when(
                workspaceQueryService.findDetail(
                        1L,
                        10L
                )
        ).thenThrow(new WorkspaceException(WorkspaceErrorCode.WORKSPACE_NOT_FOUND));

        // when
        ResultActions result = mockMvc.perform(
                get(
                        "/workspaces/{workspaceId}",
                        1L
                )
        );

        // then
        result.andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("WORKSPACE_NOT_FOUND"));
    }

    @Test
    @DisplayName("인증된 멤버가 목록 조회하면 200과 ID·이름 목록을 반환한다")
    void list_success() throws Exception {
        // given
        SecurityContextHolder.getContext()
                .setAuthentication(memberAuthentication());
        when(workspaceQueryService.findAllByMemberId(10L)).thenReturn(
                new WorkspaceListResult(
                        List.of(
                                new WorkspaceListItemResult(
                                        2L,
                                        "최근 팀"
                                ),
                                new WorkspaceListItemResult(
                                        1L,
                                        "이전 팀"
                                )
                        )
                )
        );

        // when
        ResultActions result = mockMvc.perform(get("/workspaces"));

        // then
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.workspaces").isArray())
                .andExpect(jsonPath("$.workspaces.length()").value(2))
                .andExpect(jsonPath("$.workspaces[0].id").value(2L))
                .andExpect(jsonPath("$.workspaces[0].name").value("최근 팀"))
                .andExpect(jsonPath("$.workspaces[0].role").doesNotExist())
                .andExpect(jsonPath("$.workspaces[0].joinedAt").doesNotExist())
                .andExpect(jsonPath("$.workspaces[0].createdAt").doesNotExist())
                .andExpect(jsonPath("$.workspaces[1].id").value(1L))
                .andExpect(jsonPath("$.workspaces[1].name").value("이전 팀"));
        verify(workspaceQueryService).findAllByMemberId(10L);
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
