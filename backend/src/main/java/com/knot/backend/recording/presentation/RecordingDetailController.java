package com.knot.backend.recording.presentation;

import com.knot.backend.auth.domain.AuthenticatedMember;
import com.knot.backend.recording.application.RecordingDetailService;
import com.knot.backend.recording.application.dto.result.RecordingDetailResult;
import com.knot.backend.recording.presentation.dto.response.RecordingDetailResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/recordings")
@RequiredArgsConstructor
public class RecordingDetailController implements RecordingDetailApi {
    private final RecordingDetailService recordingDetailService;

    @Override
    @GetMapping("/{recordingId}")
    public RecordingDetailResponse findDetail(
            @PathVariable long workspaceId,
            @PathVariable long recordingId,
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember
    ) {
        RecordingDetailResult result = recordingDetailService.find(
                workspaceId,
                authenticatedMember.getMemberId(),
                recordingId
        );
        return RecordingDetailResponse.from(result);
    }
}
