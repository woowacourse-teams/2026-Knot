package com.knot.backend.recording.presentation;

import com.knot.backend.auth.domain.AuthenticatedMember;
import com.knot.backend.recording.application.RecordingStartService;
import com.knot.backend.recording.application.dto.result.RecordingStartResult;
import com.knot.backend.recording.presentation.dto.request.RecordingStartRequest;
import com.knot.backend.recording.presentation.dto.response.RecordingStartResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/recordings")
@RequiredArgsConstructor
public class RecordingStartController implements RecordingStartApi {
    private final RecordingStartService recordingStartService;

    @Override
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<RecordingStartResponse> start(
            @Positive(message = "워크스페이스 ID는 양수여야 합니다") @PathVariable long workspaceId,
            @Valid @RequestBody RecordingStartRequest request,
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember
    ) {
        RecordingStartResult result = recordingStartService.start(
                workspaceId,
                authenticatedMember.getMemberId(),
                request.toCommand()
        );
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(RecordingStartResponse.from(result));
    }
}
