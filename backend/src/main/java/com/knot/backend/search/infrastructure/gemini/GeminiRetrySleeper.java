package com.knot.backend.search.infrastructure.gemini;

import java.time.Duration;

@FunctionalInterface
interface GeminiRetrySleeper {

    void sleep(Duration duration) throws InterruptedException;
}
