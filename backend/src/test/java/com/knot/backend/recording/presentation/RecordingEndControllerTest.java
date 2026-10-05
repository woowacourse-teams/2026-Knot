package com.knot.backend.recording.presentation;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.knot.backend.auth.domain.AuthenticatedMember;
import com.knot.backend.global.exception.GlobalExceptionHandler;
import com.knot.backend.recording.application.RecordingEndService;
import com.knot.backend.recording.application.dto.result.RecordingEndResult;
import com.knot.backend.recording.domain.RecordingErrorCode;
import com.knot.backend.recording.domain.RecordingException;
import com.knot.backend.recording.domain.RecordingStatus;
import java.time.Instant;
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

class RecordingEndControllerTest {
    private static final long WORKSPACE_ID = 12L;
    private static final long MEMBER_ID = 1L;
    private static final long RECORDING_ID = 34L;
    private static final Instant ENDED_AT = Instant.parse("2026-10-05T00:10:00Z");

    private RecordingEndService recordingEndService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        recordingEndService = mock(RecordingEndService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new RecordingEndController(recordingEndService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("녹음을 종료하면 200과 ENDED 상태, 서버 종료 시각을 반환한다")
    void end_success() throws Exception {
        // given
        SecurityContextHolder.getContext()
                .setAuthentication(memberAuthentication());
        when(
                recordingEndService.end(
                        WORKSPACE_ID,
                        MEMBER_ID,
                        RECORDING_ID
                )
        ).thenReturn(
                new RecordingEndResult(
                        RECORDING_ID,
                        RecordingStatus.ENDED,
                        ENDED_AT
                )
        );

        // when
        ResultActions result = mockMvc.perform(
                post(
                        "/api/v1/workspaces/{workspaceId}/recordings/{recordingId}/end",
                        WORKSPACE_ID,
                        RECORDING_ID
                )
        );

        // then
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.recordingId").value(RECORDING_ID))
                .andExpect(jsonPath("$.status").value("ENDED"))
                .andExpect(jsonPath("$.endedAt").value("2026-10-05T00:10:00Z"));
    }

    @Test
    @DisplayName("시작자가 아니면 RECORDING_CONTROL_DENIED 403 응답을 반환한다")
    void end_failure_controlDenied() throws Exception {
        // given
        SecurityContextHolder.getContext()
                .setAuthentication(memberAuthentication());
        when(
                recordingEndService.end(
                        WORKSPACE_ID,
                        MEMBER_ID,
                        RECORDING_ID
                )
        ).thenThrow(new RecordingException(RecordingErrorCode.RECORDING_CONTROL_DENIED));

        // when
        ResultActions result = mockMvc.perform(
                post(
                        "/api/v1/workspaces/{workspaceId}/recordings/{recordingId}/end",
                        WORKSPACE_ID,
                        RECORDING_ID
                )
        );

        // then
        result.andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("RECORDING_CONTROL_DENIED"))
                .andExpect(jsonPath("$.message").value("녹음을 시작한 사용자만 제어할 수 있습니다"));
    }

    @Test
    @DisplayName("숫자가 아닌 녹음 ID는 400을 반환하고 서비스를 호출하지 않는다")
    void end_failure_nonNumericRecordingId() throws Exception {
        // given
        SecurityContextHolder.getContext()
                .setAuthentication(memberAuthentication());

        // when
        ResultActions result = mockMvc.perform(
                post(
                        "/api/v1/workspaces/{workspaceId}/recordings/abc/end",
                        WORKSPACE_ID
                )
        );

        // then
        result.andExpect(status().isBadRequest());
        verifyNoInteractions(recordingEndService);
    }

    private UsernamePasswordAuthenticationToken memberAuthentication() {
        return new UsernamePasswordAuthenticationToken(
                AuthenticatedMember.of(
                        MEMBER_ID,
                        "octocat",
                        null
                ),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
    }
}
