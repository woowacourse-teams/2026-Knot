package com.knot.backend.chat.application;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class ActiveChatStreamRegistry {
    private final Set<Long> activeSessionIds = ConcurrentHashMap.newKeySet();

    public boolean tryAcquire(long sessionId) {
        return activeSessionIds.add(sessionId);
    }

    public void release(long sessionId) {
        activeSessionIds.remove(sessionId);
    }
}
