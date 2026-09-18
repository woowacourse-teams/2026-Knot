package com.knot.backend.chat.application;

import java.util.concurrent.atomic.AtomicBoolean;

public final class ChatStreamHandle {
    private final Runnable cancelAction;
    private final AtomicBoolean cancelled = new AtomicBoolean();
    private final Object completionLock = new Object();
    private boolean completionStarted;

    public ChatStreamHandle(Runnable cancelAction) {
        this.cancelAction = cancelAction;
    }

    public boolean cancel() {
        synchronized (completionLock) {
            if (completionStarted || !cancelled.compareAndSet(
                    false,
                    true
            )) {
                return false;
            }
        }
        cancelAction.run();
        return true;
    }

    public boolean beginCompletion() {
        synchronized (completionLock) {
            if (cancelled.get()) {
                return false;
            }
            completionStarted = true;
            return true;
        }
    }

    public boolean isCancelled() {
        return cancelled.get();
    }
}
