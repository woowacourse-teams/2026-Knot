package com.knot.backend.workspace.infrastructure.notion.security;

import com.knot.backend.workspace.application.ContentSourceStateGenerator;
import java.security.SecureRandom;
import java.util.Base64;

public class SecureNotionOAuthStateGenerator implements ContentSourceStateGenerator {
    private static final int STATE_BYTES = 32;

    private final SecureRandom secureRandom;

    public SecureNotionOAuthStateGenerator() {
        this(new SecureRandom());
    }

    SecureNotionOAuthStateGenerator(SecureRandom secureRandom) {
        this.secureRandom = secureRandom;
    }

    @Override
    public String generate() {
        byte[] state = new byte[STATE_BYTES];
        secureRandom.nextBytes(state);
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(state);
    }
}
