package com.knot.backend.document.presentation;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.knot.backend.auth.domain.AuthenticatedMember;
import com.knot.backend.document.application.DocumentGenerationJobListService;
import com.knot.backend.document.application.DocumentGenerationJobRetryService;
import com.knot.backend.document.application.dto.result.DocumentGenerationJobRetryResult;
import com.knot.backend.document.domain.DocumentErrorCode;
import com.knot.backend.document.domain.DocumentException;
import com.knot.backend.document.domain.DocumentGenerationJobStatus;
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
    private DocumentGenerationJobRetryService retryService;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(DocumentGenerationJobListService.class);
        retryService = mock(DocumentGenerationJobRetryService.class);
        mvc = MockMvcBuilders.standaloneSetup(
                new DocumentGenerationJobController(
                        service,
                        retryService
                )
        )
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
    @DisplayName("빈 본문 POST는 현재 멤버로 재시도하고 202 접수 결과를 반환한다")
    void retryDocumentGenerationJob_success() throws Exception {
        // given
        when(
                retryService.retry(
                        1,
                        2,
                        88
                )
        ).thenReturn(
                new DocumentGenerationJobRetryResult(
                        88,
                        DocumentGenerationJobStatus.QUEUED,
                        2
                )
        );

        // when & then
        mvc.perform(
                post(
                        PATH + "/{jobId}/retry",
                        1,
                        88
                )
        )
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.jobId").value(88))
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andExpect(jsonPath("$.attemptCount").value(2));
        verify(retryService).retry(
                1,
                2,
                88
        );
        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("없는 Job은 기존 예외 처리기로 404와 Job 오류를 반환한다")
    void retryDocumentGenerationJob_failure_missingJob() throws Exception {
        // given
        when(
                retryService.retry(
                        1,
                        2,
                        88
                )
        ).thenThrow(new DocumentException(DocumentErrorCode.DOCUMENT_GENERATION_JOB_NOT_FOUND));

        // when & then
        mvc.perform(
                post(
                        PATH + "/{jobId}/retry",
                        1,
                        88
                )
        )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DOCUMENT_GENERATION_JOB_NOT_FOUND"));
    }

    @Test
    @DisplayName("재시도 불가 Job은 409로 응답한다")
    void retryDocumentGenerationJob_failure_notAllowed() throws Exception {
        // given
        when(
                retryService.retry(
                        1,
                        2,
                        88
                )
        ).thenThrow(new DocumentException(DocumentErrorCode.RETRY_NOT_ALLOWED));

        // when & then
        mvc.perform(
                post(
                        PATH + "/{jobId}/retry",
                        1,
                        88
                )
        )
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RETRY_NOT_ALLOWED"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "9223372036854775808"})
    @DisplayName("잘못된 Job ID 형식은 서비스 호출 전에 400이다")
    void retryDocumentGenerationJob_failure_invalidPath(String jobId) throws Exception {
        // when & then
        mvc.perform(
                post(
                        PATH + "/{jobId}/retry",
                        1,
                        jobId
                )
        )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
        verifyNoInteractions(
                service,
                retryService
        );
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
