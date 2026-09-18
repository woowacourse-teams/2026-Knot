package com.knot.backend.chat.infrastructure;

import com.knot.backend.chat.application.LlmStream;
import java.util.Iterator;
import java.util.List;

public class FakeLlmStream implements LlmStream {
    private final Iterator<String> chunks;

    public FakeLlmStream(List<String> chunks) {
        this.chunks = chunks.iterator();
    }

    @Override
    public boolean hasNext() {
        return chunks.hasNext();
    }

    @Override
    public String next() {
        return chunks.next();
    }

    @Override
    public void close() {
        // fake stream에는 해제할 외부 자원이 없다.
    }
}
