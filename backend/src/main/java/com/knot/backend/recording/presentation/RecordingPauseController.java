package com.knot.backend.recording.presentation;

import com.knot.backend.auth.domain.AuthenticatedMember;
import com.knot.backend.recording.application.RecordingPauseService;
import com.knot.backend.recording.application.dto.result.RecordingPauseResult;
import com.knot.backend.recording.presentation.dto.request.RecordingControlRequest;
import com.knot.backend.recording.presentation.dto.response.RecordingPauseResponse;
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
public class RecordingPauseController implements RecordingPauseApi {
    private final RecordingPauseService recordingPauseService;

    @Override
    @PostMapping("/{recordingId}/pause")
    public RecordingPauseResponse pause(
            @PathVariable long workspaceId,
            @PathVariable long recordingId,
            @Valid @RequestBody RecordingControlRequest request,
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember
    ) {
        RecordingPauseResult result = recordingPauseService.pause(
                workspaceId,
                authenticatedMember.getMemberId(),
                recordingId,
                request.toCommand()
        );
        return RecordingPauseResponse.from(result);
    }
}
