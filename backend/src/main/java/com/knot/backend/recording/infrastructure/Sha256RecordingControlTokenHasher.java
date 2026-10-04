package com.knot.backend.recording.infrastructure;

import com.knot.backend.recording.application.RecordingControlTokenHasher;
import com.knot.backend.recording.domain.RecordingErrorCode;
import com.knot.backend.recording.domain.RecordingException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

@Component
public class Sha256RecordingControlTokenHasher implements RecordingControlTokenHasher {

    @Override
    public String hash(String controlToken) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(controlToken.getBytes(StandardCharsets.US_ASCII))
                    );
        } catch (NoSuchAlgorithmException exception) {
            throw new RecordingException(RecordingErrorCode.RECORDING_CONTROL_HASH_FAILED);
        }
    }
}
