package com.knot.backend.workspace.presentation;

import static org.hamcrest.Matchers.nullValue;
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
import com.knot.backend.workspace.application.ImportedPageTreeQueryService;
import com.knot.backend.workspace.application.dto.result.ImportedPageTreeItemResult;
import com.knot.backend.workspace.domain.ImportedPageErrorCode;
import com.knot.backend.workspace.domain.ImportedPageException;
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

class NotionPageTreeControllerTest {
    private ImportedPageTreeQueryService pageTreeQueryService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        pageTreeQueryService = mock(ImportedPageTreeQueryService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new NotionPageTreeController(pageTreeQueryService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .addFilters(new NotionPageTreeCacheControlFilter())
                .build();
        SecurityContextHolder.getContext()
                .setAuthentication(memberAuthentication());
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @DisplayName("Workspace 멤버가 Page Tree를 조회하면 평면 배열과 no-store를 반환한다")
    @Test
    void tree_success() throws Exception {
        // given
        when(
                pageTreeQueryService.findTree(
                        1L,
                        10L
                )
        ).thenReturn(
                List.of(
                        new ImportedPageTreeItemResult(
                                1L,
                                null,
                                "루트",
                                0,
                                "https://www.notion.so/root"
                        ),
                        new ImportedPageTreeItemResult(
                                2L,
                                1L,
                                "자식",
                                0,
                                "https://www.notion.so/child"
                        )
                )
        );

        // when
        ResultActions result = mockMvc.perform(
                get(
                        "/api/v1/workspaces/{workspaceId}/notion-pages/tree",
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
                .andExpect(jsonPath("$[0].id").value(1L))
                .andExpect(jsonPath("$[0].parentPageId").value(nullValue()))
                .andExpect(jsonPath("$[0].title").value("루트"))
                .andExpect(jsonPath("$[0].position").value(0))
                .andExpect(jsonPath("$[0].notionUrl").value("https://www.notion.so/root"))
                .andExpect(jsonPath("$[0].markdownContent").doesNotExist())
                .andExpect(jsonPath("$[1].parentPageId").value(1L));
        verify(pageTreeQueryService).findTree(
                1L,
                10L
        );
    }

    @DisplayName("Workspace ID 형식이 잘못되면 no-store와 INVALID_PARAMETER 400을 반환한다")
    @Test
    void tree_failure_invalidWorkspaceIdType() throws Exception {
        // given
        String workspaceId = "not-a-number";

        // when
        ResultActions result = mockMvc.perform(
                get(
                        "/api/v1/workspaces/{workspaceId}/notion-pages/tree",
                        workspaceId
                )
        );

        // then
        result.andExpect(status().isBadRequest())
                .andExpect(
                        header().string(
                                HttpHeaders.CACHE_CONTROL,
                                "no-store"
                        )
                )
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("workspaceId"));
        verifyNoInteractions(pageTreeQueryService);
    }

    @DisplayName("Page 계층이 잘못되면 no-store와 NOTION_PAGE_TREE_INVALID 500을 반환한다")
    @Test
    void tree_failure_invalidTree() throws Exception {
        // given
        when(
                pageTreeQueryService.findTree(
                        1L,
                        10L
                )
        ).thenThrow(new ImportedPageException(ImportedPageErrorCode.IMPORTED_PAGE_TREE_INVALID));

        // when
        ResultActions result = mockMvc.perform(
                get(
                        "/api/v1/workspaces/{workspaceId}/notion-pages/tree",
                        1L
                )
        );

        // then
        result.andExpect(status().isInternalServerError())
                .andExpect(
                        header().string(
                                HttpHeaders.CACHE_CONTROL,
                                "no-store"
                        )
                )
                .andExpect(jsonPath("$.code").value("NOTION_PAGE_TREE_INVALID"))
                .andExpect(jsonPath("$.message").value("Notion Page Tree를 조회할 수 없습니다"));
    }

    private UsernamePasswordAuthenticationToken memberAuthentication() {
        return new UsernamePasswordAuthenticationToken(
                AuthenticatedMember.of(
                        10L,
                        "member",
                        null
                ),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
    }
}
