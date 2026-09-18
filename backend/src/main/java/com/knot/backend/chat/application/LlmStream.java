package com.knot.backend.chat.application;

public interface LlmStream extends AutoCloseable {

    boolean hasNext();

    String next();

    @Override
    void close();
}
