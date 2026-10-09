package com.knot.backend.recording.presentation;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.knot.backend.auth.domain.AuthenticatedMember;
import com.knot.backend.global.exception.GlobalExceptionHandler;
import com.knot.backend.recording.application.RecordingDetailService;
import com.knot.backend.recording.application.dto.result.RecordingDetailResult;
import com.knot.backend.recording.domain.RecordingAudioUploadStatus;
import com.knot.backend.recording.domain.RecordingEndReason;
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

class RecordingDetailControllerTest {
    private static final long WORKSPACE_ID = 12L;
    private static final long MEMBER_ID = 1L;
    private static final long RECORDING_ID = 34L;
    private static final Instant STARTED_AT = Instant.parse("2026-10-09T00:00:00Z");

    private RecordingDetailService recordingDetailService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        recordingDetailService = mock(RecordingDetailService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new RecordingDetailController(recordingDetailService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("종료되고 업로드가 끝난 본인 녹음을 조회하면 200과 세션·업로드 상태를 반환한다")
    void findDetail_success() throws Exception {
        // given
        SecurityContextHolder.getContext()
                .setAuthentication(memberAuthentication());
        when(
                recordingDetailService.find(
                        WORKSPACE_ID,
                        MEMBER_ID,
                        RECORDING_ID
                )
        ).thenReturn(
                new RecordingDetailResult(
                        RECORDING_ID,
                        RecordingStatus.ENDED,
                        STARTED_AT,
                        60_000L,
                        STARTED_AT.plusSeconds(60),
                        RecordingEndReason.USER_ENDED,
                        null,
                        STARTED_AT.plusSeconds(90),
                        RecordingAudioUploadStatus.COMPLETED,
                        3L,
                        STARTED_AT.plusSeconds(80),
                        7_200_000L
                )
        );

        // when
        ResultActions result = mockMvc.perform(
                get(
                        "/api/v1/workspaces/{workspaceId}/recordings/{recordingId}",
                        WORKSPACE_ID,
                        RECORDING_ID
                )
        );

        // then
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.recordingId").value(RECORDING_ID))
                .andExpect(jsonPath("$.status").value("ENDED"))
                .andExpect(jsonPath("$.startedAt").value("2026-10-09T00:00:00Z"))
                .andExpect(jsonPath("$.elapsedMillis").value(60_000L))
                .andExpect(jsonPath("$.endedAt").value("2026-10-09T00:01:00Z"))
                .andExpect(jsonPath("$.endReason").value("USER_ENDED"))
                .andExpect(jsonPath("$.expiresAt").isEmpty())
                .andExpect(jsonPath("$.serverNow").value("2026-10-09T00:01:30Z"))
                .andExpect(jsonPath("$.audioUploadStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.uploadId").value(3L))
                .andExpect(jsonPath("$.completedAt").value("2026-10-09T00:01:20Z"))
                .andExpect(jsonPath("$.maxDurationMillis").value(7_200_000L));
    }

    @Test
    @DisplayName("요청한 Workspace에 없는 녹음이면 RECORDING_NOT_FOUND 404를 반환한다")
    void findDetail_failure_notFound() throws Exception {
        // given
        SecurityContextHolder.getContext()
                .setAuthentication(memberAuthentication());
        when(
                recordingDetailService.find(
                        WORKSPACE_ID,
                        MEMBER_ID,
                        RECORDING_ID
                )
        ).thenThrow(new RecordingException(RecordingErrorCode.RECORDING_NOT_FOUND));

        // when
        ResultActions result = mockMvc.perform(
                get(
                        "/api/v1/workspaces/{workspaceId}/recordings/{recordingId}",
                        WORKSPACE_ID,
                        RECORDING_ID
                )
        );

        // then
        result.andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RECORDING_NOT_FOUND"));
    }

    @Test
    @DisplayName("숫자가 아닌 녹음 ID는 400을 반환하고 서비스를 호출하지 않는다")
    void findDetail_failure_nonNumericRecordingId() throws Exception {
        // given
        SecurityContextHolder.getContext()
                .setAuthentication(memberAuthentication());

        // when
        ResultActions result = mockMvc.perform(
                get(
                        "/api/v1/workspaces/{workspaceId}/recordings/abc",
                        WORKSPACE_ID
                )
        );

        // then
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
        verifyNoInteractions(recordingDetailService);
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
