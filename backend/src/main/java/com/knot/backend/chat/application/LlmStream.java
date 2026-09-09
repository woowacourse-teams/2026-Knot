package com.knot.backend.chat.application;

import com.knot.backend.chat.domain.LlmUsage;
import java.util.Optional;

public interface LlmStream extends AutoCloseable {

    boolean hasNext();

    String next();

    /**
     * 스트림이 끝난 뒤의 토큰 사용량(데스크톱 기획서 6.2 계측 행, 로드맵 B2). 사용량을 알려 주지 않는 어댑터는 빈 값이며
     * 그 답변의 사용량 컬럼은 전부 NULL로 남는다.
     */
    default Optional<LlmUsage> usage() {
        return Optional.empty();
    }

    @Override
    void close();
}
