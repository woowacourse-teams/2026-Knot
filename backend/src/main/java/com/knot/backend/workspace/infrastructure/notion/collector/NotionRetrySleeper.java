package com.knot.backend.workspace.infrastructure.notion.collector;

import java.time.Duration;

@FunctionalInterface
interface NotionRetrySleeper {

    void sleep(Duration duration) throws InterruptedException;
}
