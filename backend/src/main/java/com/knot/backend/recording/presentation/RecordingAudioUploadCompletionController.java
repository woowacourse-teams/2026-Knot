package com.knot.backend.recording.presentation;

import com.knot.backend.auth.domain.AuthenticatedMember;
import com.knot.backend.recording.application.RecordingAudioUploadCompletionService;
import com.knot.backend.recording.application.dto.result.RecordingAudioUploadCompletionResult;
import com.knot.backend.recording.presentation.dto.request.RecordingAudioUploadCompletionRequest;
import com.knot.backend.recording.presentation.dto.response.RecordingAudioUploadCompletionResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/recordings")
@RequiredArgsConstructor
public class RecordingAudioUploadCompletionController implements RecordingAudioUploadCompletionApi {
    private final RecordingAudioUploadCompletionService recordingAudioUploadCompletionService;

    @Override
    @PostMapping("/{recordingId}/audio-upload-complete")
    public RecordingAudioUploadCompletionResponse complete(
            @PathVariable long workspaceId,
            @PathVariable long recordingId,
            @Valid @RequestBody RecordingAudioUploadCompletionRequest request,
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember
    ) {
        RecordingAudioUploadCompletionResult result = recordingAudioUploadCompletionService.complete(
                workspaceId,
                authenticatedMember.getMemberId(),
                recordingId,
                request.uploadId()
        );
        return RecordingAudioUploadCompletionResponse.from(result);
    }
}
