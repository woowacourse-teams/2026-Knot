package com.knot.backend.recording.application;

public interface RecordingControlTokenHasher {

    String hash(String controlToken);
}
