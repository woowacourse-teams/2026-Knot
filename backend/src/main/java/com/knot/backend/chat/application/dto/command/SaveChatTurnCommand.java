package com.knot.backend.chat.application.dto.command;

import com.knot.backend.search.domain.SearchReferenceCandidate;
import java.util.List;

/**
 * CLI 에이전트가 만든 한 턴(질문·답변·근거 ≤8). 근거 배열의 순서가 rank다(기획서 6.4 턴 저장 API).
 */
public record SaveChatTurnCommand(
        String question,
        String answer,
        List<SearchReferenceCandidate> references
) {

    public SaveChatTurnCommand {
        references = List.copyOf(references);
    }
}
