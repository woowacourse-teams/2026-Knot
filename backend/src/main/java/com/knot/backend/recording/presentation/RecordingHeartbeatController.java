package com.knot.backend.recording.presentation;

import com.knot.backend.auth.domain.AuthenticatedMember;
import com.knot.backend.recording.application.RecordingHeartbeatService;
import com.knot.backend.recording.application.dto.result.RecordingHeartbeatResult;
import com.knot.backend.recording.presentation.dto.request.RecordingControlRequest;
import com.knot.backend.recording.presentation.dto.response.RecordingHeartbeatResponse;
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
public class RecordingHeartbeatController implements RecordingHeartbeatApi {
    private final RecordingHeartbeatService recordingHeartbeatService;

    @Override
    @PostMapping("/{recordingId}/heartbeat")
    public RecordingHeartbeatResponse heartbeat(
            @PathVariable long workspaceId,
            @PathVariable long recordingId,
            @Valid @RequestBody RecordingControlRequest request,
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember
    ) {
        RecordingHeartbeatResult result = recordingHeartbeatService.heartbeat(
                workspaceId,
                authenticatedMember.getMemberId(),
                recordingId,
                request.toCommand()
        );
        return RecordingHeartbeatResponse.from(result);
    }
}
