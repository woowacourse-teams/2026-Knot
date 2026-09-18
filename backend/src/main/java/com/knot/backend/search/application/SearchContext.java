package com.knot.backend.search.application;

import com.knot.backend.search.domain.SearchChunk;
import com.knot.backend.search.domain.SearchResultStatus;
import java.util.List;

public final class SearchContext {
    private static final String NO_RESULT_MESSAGE = "현재 동기화된 팀 문서에서는 관련된 정보를 찾지 못했습니다. "
            + "최신 문서가 반영되지 않았다면 동기화 후 다시 검색해보세요.";
    private static final String BROAD_QUESTION_MESSAGE = "범위가 넓어요. 최근 결정사항, 로드맵, 백엔드 진행 상황 중 " + "어떤 내용을 찾고 싶나요?";
    private static final String GROUNDING_INSTRUCTION = """
            다음 규칙을 반드시 지켜 답변하세요.
            - 아래 근거 문서에 실제로 적힌 내용만 답변하세요.
            - 근거에 없는 사실, 날짜, 결정 이유를 일반 지식으로 보완하거나 추측하지 마세요.
            - 문서마다 내용이 다르면 어느 하나를 최종 정답으로 고르지 말고 충돌을 명시하세요.
            - 답변에 사용한 문서 제목을 함께 언급하세요.
            - 근거로 답할 수 없으면 현재 동기화된 문서에서 확인할 수 없다고 말하세요.

            """;

    private final SearchResultStatus status;
    private final List<SearchChunk> references;
    private final int maxContextCharacters;

    private SearchContext(
            SearchResultStatus status,
            List<SearchChunk> references,
            int maxContextCharacters
    ) {
        this.status = status;
        this.references = List.copyOf(references);
        this.maxContextCharacters = maxContextCharacters;
    }

    public static SearchContext ready(
            List<SearchChunk> references,
            int maxContextCharacters
    ) {
        return new SearchContext(
                SearchResultStatus.READY,
                references,
                maxContextCharacters
        );
    }

    public static SearchContext noResult() {
        return new SearchContext(
                SearchResultStatus.NO_RESULT,
                List.of(),
                0
        );
    }

    public static SearchContext needsClarification() {
        return new SearchContext(
                SearchResultStatus.NEEDS_CLARIFICATION,
                List.of(),
                0
        );
    }

    public boolean isReady() {
        return status == SearchResultStatus.READY;
    }

    public boolean isNoResult() {
        return status == SearchResultStatus.NO_RESULT;
    }

    public List<SearchChunk> references() {
        return references;
    }

    public String fallbackAnswer() {
        return switch (status) {
            case NO_RESULT -> NO_RESULT_MESSAGE;
            case NEEDS_CLARIFICATION -> BROAD_QUESTION_MESSAGE;
            case READY -> throw new IllegalStateException("준비된 검색 결과에는 fallback 답변이 없습니다");
        };
    }

    public String groundingPrompt() {
        StringBuilder prompt = new StringBuilder(GROUNDING_INSTRUCTION);
        int usedCharacters = GROUNDING_INSTRUCTION.length();
        for (int index = 0; index < references.size() && usedCharacters < maxContextCharacters; index++) {
            SearchChunk reference = references.get(index);
            String content = reference.content();
            int remainingCharacters = maxContextCharacters - usedCharacters;
            if (content.length() > remainingCharacters) {
                content = content.substring(
                        0,
                        remainingCharacters
                );
            }
            prompt.append("[근거 문서 ")
                    .append(index + 1)
                    .append("]\n제목: ")
                    .append(reference.title())
                    .append("\n문서 ID: ")
                    .append(reference.importedPageId())
                    .append("\n문서 링크: ")
                    .append(reference.sourceUrl())
                    .append("\n내용:\n")
                    .append(content)
                    .append("\n\n");
            usedCharacters = prompt.length();
        }
        return prompt.toString();
    }
}
