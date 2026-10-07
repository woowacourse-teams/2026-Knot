package com.knot.backend.document.presentation;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.knot.backend.auth.domain.AuthenticatedMember;
import com.knot.backend.document.application.DocumentGenerationJobListService;
import com.knot.backend.document.application.dto.query.DocumentGenerationJobListParameters;
import com.knot.backend.document.application.dto.result.DocumentGenerationJobListResult;
import com.knot.backend.global.exception.GlobalExceptionHandler;
import com.knot.backend.workspace.domain.WorkspaceErrorCode;
import com.knot.backend.workspace.domain.WorkspaceException;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class DocumentGenerationJobControllerTest {

    private static final String PATH = "/api/v1/workspaces/{workspaceId}/document-generation-jobs";

    private DocumentGenerationJobListService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(DocumentGenerationJobListService.class);
        mvc = MockMvcBuilders.standaloneSetup(new DocumentGenerationJobController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                AuthenticatedMember.of(
                                        2,
                                        "멤버",
                                        null
                                ),
                                null,
                                List.of(new SimpleGrantedAuthority("ROLE_USER"))
                        )
                );
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("경로·현재 멤버·기본 페이지 크기를 서비스에 전달한다")
    void findDocumentGenerationJobs_success() throws Exception {
        // given
        when(
                service.find(
                        1,
                        2,
                        DocumentGenerationJobListParameters.of(
                                null,
                                null
                        )
                )
        ).thenReturn(
                new DocumentGenerationJobListResult(
                        List.of(),
                        null
                )
        );

        // when & then
        mvc.perform(
                get(
                        PATH,
                        1
                )
        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.nextCursor").isEmpty());
        verify(service).find(
                1,
                2,
                DocumentGenerationJobListParameters.of(
                        null,
                        20
                )
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "101", "-1", "abc", "9223372036854775808"})
    @DisplayName("크기 형식·범위 오류는 서비스를 호출하지 않고 400이다")
    void findDocumentGenerationJobs_failure_invalidSize(String size) throws Exception {
        // when & then
        mvc.perform(
                get(
                        PATH,
                        1
                ).param(
                        "size",
                        size
                )
        )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("비멤버 예외는 기존 처리기로 403을 반환한다")
    void findDocumentGenerationJobs_failure_nonMember() throws Exception {
        // given
        when(
                service.find(
                        1,
                        2,
                        DocumentGenerationJobListParameters.of(
                                null,
                                null
                        )
                )
        ).thenThrow(new WorkspaceException(WorkspaceErrorCode.WORKSPACE_ACCESS_DENIED));

        // when & then
        mvc.perform(
                get(
                        PATH,
                        1
                )
        )
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("WORKSPACE_ACCESS_DENIED"));
    }
}
