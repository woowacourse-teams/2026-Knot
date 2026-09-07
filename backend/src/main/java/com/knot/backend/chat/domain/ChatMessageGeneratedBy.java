package com.knot.backend.chat.domain;

/**
 * 답변을 누가 만들었는지. 서버 LLM 경로는 SERVER, 데스크톱이 사용자 LLM으로 만들어 저장한 답변은 CLIENT다(기획서
 * 6.4).
 */
public enum ChatMessageGeneratedBy {
    SERVER,
    CLIENT,
}
