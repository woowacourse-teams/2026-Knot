package com.knot.backend.search.application;

/**
 * 임베딩 요청의 용도. Gemini처럼 색인용·질의용 벡터를 구분하는 provider가 taskType으로 쓴다. 구분이 없는
 * provider는 무시한다.
 */
public enum EmbeddingTask {
    DOCUMENT,
    QUERY
}
