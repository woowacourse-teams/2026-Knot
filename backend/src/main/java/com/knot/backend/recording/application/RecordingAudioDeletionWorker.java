package com.knot.backend.recording.application;

import com.knot.backend.recording.application.dto.result.RecordingAudioDeletionExecution;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecordingAudioDeletionWorker {

    private final RecordingAudioDeletionStateService state;
    private final RecordingAudioStorage storage;

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void execute(long taskId) {
        Optional<RecordingAudioDeletionExecution> claim = state.claim(taskId);
        if (claim.isEmpty()) {
            return;
        }
        RecordingAudioDeletionExecution execution = claim.orElseThrow();
        try {
            storage.deleteStoredObject(execution.storageKey());
        } catch (RuntimeException exception) {
            state.fail(
                    taskId,
                    execution.attemptCount()
            );
            log.warn(
                    "Audio deletion deferred taskId={} attempt={}",
                    taskId,
                    execution.attemptCount()
            );
            return;
        }
        state.succeed(
                taskId,
                execution.attemptCount()
        );
    }
}
