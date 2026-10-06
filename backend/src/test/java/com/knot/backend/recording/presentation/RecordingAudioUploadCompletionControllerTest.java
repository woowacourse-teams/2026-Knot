package com.knot.backend.recording.presentation;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.knot.backend.auth.domain.AuthenticatedMember;
import com.knot.backend.global.exception.GlobalExceptionHandler;
import com.knot.backend.recording.application.RecordingAudioUploadCompletionService;
import com.knot.backend.recording.application.dto.result.RecordingAudioUploadCompletionResult;
import com.knot.backend.recording.domain.RecordingAudioUploadStatus;
import java.time.Instant;
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
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class RecordingAudioUploadCompletionControllerTest {
    private static final long WORKSPACE_ID = 12L;
    private static final long MEMBER_ID = 1L;
    private static final long RECORDING_ID = 34L;
    private static final String RECORDING_PATH = "/api/v1/workspaces/{workspaceId}/recordings/{recordingId}";
    private static final String PATH = RECORDING_PATH + "/audio-upload-complete";
    private static final String BODY = "{\"uploadId\":5}";
    private static final Instant COMPLETED_AT = Instant.parse("2026-10-05T00:15:00Z");

    private RecordingAudioUploadCompletionService recordingAudioUploadCompletionService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        recordingAudioUploadCompletionService = mock(RecordingAudioUploadCompletionService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new RecordingAudioUploadCompletionController(recordingAudioUploadCompletionService))
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

    @Test
    @DisplayName("업로드 완료를 확인하면 200과 COMPLETED 상태, 완료 시각을 반환한다")
    void complete_success() throws Exception {
        // given
        when(
                recordingAudioUploadCompletionService.complete(
                        WORKSPACE_ID,
                        MEMBER_ID,
                        RECORDING_ID,
                        5L
                )
        ).thenReturn(
                new RecordingAudioUploadCompletionResult(
                        RECORDING_ID,
                        5L,
                        RecordingAudioUploadStatus.COMPLETED,
                        COMPLETED_AT
                )
        );

        // when
        ResultActions result = mockMvc.perform(request());

        // then
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.recordingId").value(RECORDING_ID))
                .andExpect(jsonPath("$.uploadId").value(5))
                .andExpect(jsonPath("$.uploadStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.completedAt").value("2026-10-05T00:15:00Z"));
    }

    @Test
    @DisplayName("uploadId가 없으면 VALIDATION_ERROR 400을 반환하고 서비스를 호출하지 않는다")
    void complete_failure_missingUploadId() throws Exception {
        // given
        String body = "{}";

        // when
        ResultActions result = mockMvc.perform(
                post(
                        PATH,
                        WORKSPACE_ID,
                        RECORDING_ID
                ).contentType(MediaType.APPLICATION_JSON)
                        .content(body)
        );

        // then
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("uploadId"));
        verifyNoInteractions(recordingAudioUploadCompletionService);
    }

    private RequestBuilder request() {
        return post(
                PATH,
                WORKSPACE_ID,
                RECORDING_ID
        ).contentType(MediaType.APPLICATION_JSON)
                .content(BODY);
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
