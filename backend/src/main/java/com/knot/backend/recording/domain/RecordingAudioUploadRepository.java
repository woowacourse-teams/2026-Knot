package com.knot.backend.recording.domain;

import java.util.Optional;

public interface RecordingAudioUploadRepository {

    RecordingAudioUpload save(RecordingAudioUpload recordingAudioUpload);

    Optional<RecordingAudioUpload> findById(long uploadId);

    Optional<RecordingAudioUpload> findByRecordingId(long recordingId);
}
