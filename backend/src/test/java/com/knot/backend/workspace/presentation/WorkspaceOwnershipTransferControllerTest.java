package com.knot.backend.workspace.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.knot.backend.auth.domain.AuthenticatedMember;
import com.knot.backend.global.exception.GlobalExceptionHandler;
import com.knot.backend.workspace.application.WorkspaceOwnershipTransferService;
import com.knot.backend.workspace.domain.WorkspaceErrorCode;
import com.knot.backend.workspace.domain.WorkspaceException;
import java.sql.SQLException;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.UncategorizedSQLException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class WorkspaceOwnershipTransferControllerTest {
    private WorkspaceOwnershipTransferService workspaceOwnershipTransferService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        workspaceOwnershipTransferService = mock(WorkspaceOwnershipTransferService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new WorkspaceOwnershipTransferController(workspaceOwnershipTransferService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("인증된 OWNER 승계 요청은 path와 본문 값을 서비스에 전달하고 204 빈 본문을 반환한다")
    void transferOwnership_success() throws Exception {
        // given
        SecurityContextHolder.getContext()
                .setAuthentication(memberAuthentication());

        // when
        ResultActions result = mockMvc.perform(
                post(
                        "/api/v1/workspaces/{workspaceId}/ownership-transfers",
                        7L
                ).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"successorMemberId":2}
                                """)
        );

        // then
        result.andExpect(status().isNoContent())
                .andExpect(content().string(""));
        verify(workspaceOwnershipTransferService).transferOwnership(
                1L,
                7L,
                2L
        );
        verifyNoMoreInteractions(workspaceOwnershipTransferService);
    }

    @ParameterizedTest
    @MethodSource("invalidSuccessorRequests")
    @DisplayName("successorMemberId가 유효하지 않으면 VALIDATION_ERROR 400이고 서비스를 호출하지 않는다")
    void transferOwnership_failure_invalidSuccessorMemberId(
            String requestBody,
            String reason
    ) throws Exception {
        // given
        SecurityContextHolder.getContext()
                .setAuthentication(memberAuthentication());

        // when
        ResultActions result = mockMvc.perform(
                post(
                        "/api/v1/workspaces/{workspaceId}/ownership-transfers",
                        7L
                ).contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody)
        );

        // then
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("successorMemberId"))
                .andExpect(jsonPath("$.fieldErrors[0].reason").value(reason));
        verifyNoInteractions(workspaceOwnershipTransferService);
    }

    @Test
    @DisplayName("본문이 JSON 형식이 아니면 INVALID_REQUEST_BODY 400이고 서비스를 호출하지 않는다")
    void transferOwnership_failure_malformedJson() throws Exception {
        // given
        SecurityContextHolder.getContext()
                .setAuthentication(memberAuthentication());

        // when
        ResultActions result = mockMvc.perform(
                post(
                        "/api/v1/workspaces/{workspaceId}/ownership-transfers",
                        7L
                ).contentType(MediaType.APPLICATION_JSON)
                        .content("{")
        );

        // then
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST_BODY"));
        verifyNoInteractions(workspaceOwnershipTransferService);
    }

    @Test
    @DisplayName("서비스가 워크스페이스 예외를 던지면 해당 오류 코드와 상태로 변환한다")
    void transferOwnership_failure_workspaceException() throws Exception {
        // given
        SecurityContextHolder.getContext()
                .setAuthentication(memberAuthentication());
        doThrow(new WorkspaceException(WorkspaceErrorCode.WORKSPACE_OWNERSHIP_TRANSFER_TARGET_CONFLICT))
                .when(workspaceOwnershipTransferService)
                .transferOwnership(
                        1L,
                        7L,
                        2L
                );

        // when
        ResultActions result = mockMvc.perform(
                post(
                        "/api/v1/workspaces/{workspaceId}/ownership-transfers",
                        7L
                ).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"successorMemberId":2}
                                """)
        );

        // then
        result.andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("WORKSPACE_OWNERSHIP_TRANSFER_TARGET_CONFLICT"));
        verify(workspaceOwnershipTransferService).transferOwnership(
                1L,
                7L,
                2L
        );
    }

    @Test
    @DisplayName("예상하지 못한 SQL 예외는 500으로 변환하고 기술 정보를 노출하지 않는다")
    void transferOwnership_failure_unexpectedSqlException() throws Exception {
        // given
        SecurityContextHolder.getContext()
                .setAuthentication(memberAuthentication());
        doThrow(
                new UncategorizedSQLException(
                        "transfer failed",
                        "SELECT * FROM workspace_members WHERE member_id = 1",
                        new SQLException("database-secret")
                )
        ).when(workspaceOwnershipTransferService)
                .transferOwnership(
                        1L,
                        7L,
                        2L
                );

        // when
        ResultActions result = mockMvc.perform(
                post(
                        "/api/v1/workspaces/{workspaceId}/ownership-transfers",
                        7L
                ).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"successorMemberId":2}
                                """)
        );

        // then
        MvcResult mvcResult = result.andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
                .andReturn();
        assertNoTechnicalInformation(mvcResult);
        verify(workspaceOwnershipTransferService).transferOwnership(
                1L,
                7L,
                2L
        );
    }

    private static Stream<Arguments> invalidSuccessorRequests() {
        return Stream.of(
                Arguments.of(
                        """
                                {"successorMemberId":null}
                                """,
                        "승계할 멤버 ID는 필수입니다"
                ),
                Arguments.of(
                        """
                                {"successorMemberId":0}
                                """,
                        "승계할 멤버 ID는 양수여야 합니다"
                ),
                Arguments.of(
                        """
                                {"successorMemberId":-1}
                                """,
                        "승계할 멤버 ID는 양수여야 합니다"
                )
        );
    }

    private void assertNoTechnicalInformation(MvcResult mvcResult) throws Exception {
        String responseBody = mvcResult.getResponse()
                .getContentAsString();
        assertThat(responseBody).doesNotContain(
                "SELECT",
                "workspace_members",
                "member_id",
                "database-secret",
                "SQLException",
                "UncategorizedSQLException",
                "Exception"
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
