package com.knot.backend.recording.domain;

import com.knot.backend.global.exception.ProjectException;

public final class RecordingException extends ProjectException {

    public RecordingException(RecordingErrorCode errorCode) {
        super(errorCode);
    }
}
