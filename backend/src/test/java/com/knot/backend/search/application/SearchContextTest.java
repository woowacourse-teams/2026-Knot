package com.knot.backend.search.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.knot.backend.search.domain.SearchChunk;
import com.knot.backend.search.domain.SearchResultStatus;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SearchContextTest {
    private static final SearchChunk FIRST = chunk(
            0,
            "가나다라마바사"
    );
    private static final SearchChunk SECOND = chunk(
            1,
            "아자차카타파하"
    );

    @Test
    @DisplayName("근거 규칙 문장은 프롬프트 맨 앞에 그대로 들어간다")
    void groundingRules_success_prefixesPrompt() {
        // given
        SearchContext context = SearchContext.ready(
                List.of(
                        FIRST,
                        SECOND
                ),
                12000
        );

        // when
        String prompt = context.groundingPrompt();

        // then
        assertThat(SearchContext.groundingRules()).contains("다음 규칙을 반드시 지켜 답변하세요");
        assertThat(prompt).startsWith(SearchContext.groundingRules())
                .contains("[근거 문서 1]\n제목: 문서 0\n문서 ID: 201\n문서 링크: https://notion.test/0\n내용:\n가나다라마바사\n\n")
                .contains("[근거 문서 2]");
        assertThat(context.contextReferences()).extracting(SearchChunk::content)
                .containsExactly(
                        "가나다라마바사",
                        "아자차카타파하"
                );
        assertThat(context.status()).isEqualTo(SearchResultStatus.READY);
    }

    @Test
    @DisplayName("프롬프트 예산을 넘는 본문은 자르고 예산이 다한 뒤의 근거는 응답과 프롬프트에서 뺀다")
    void contextReferences_success_trimsToBudget() {
        // given
        int maxContextCharacters = SearchContext.groundingRules()
                .length() + 3;
        SearchContext context = SearchContext.ready(
                List.of(
                        FIRST,
                        SECOND
                ),
                maxContextCharacters
        );

        // when
        List<SearchChunk> contextReferences = context.contextReferences();

        // then
        assertThat(contextReferences).hasSize(1);
        assertThat(contextReferences.getFirst()).extracting(
                SearchChunk::content,
                SearchChunk::chunkIndex,
                SearchChunk::score
        )
                .containsExactly(
                        "가나다",
                        0,
                        0.9
                );
        assertThat(context.groundingPrompt()).endsWith("내용:\n가나다\n\n")
                .doesNotContain("[근거 문서 2]");
        assertThat(context.references()).extracting(SearchChunk::content)
                .containsExactly(
                        "가나다라마바사",
                        "아자차카타파하"
                );
    }

    @Test
    @DisplayName("근거 없음·넓은 질문은 안내 문구를 돌려주고 READY는 안내 문구가 없다")
    void fallbackAnswer_success_byStatus() {
        // given
        SearchContext noResult = SearchContext.noResult();
        SearchContext needsClarification = SearchContext.needsClarification();
        SearchContext ready = SearchContext.ready(
                List.of(FIRST),
                12000
        );

        // when
        String noResultAnswer = noResult.fallbackAnswer();
        String clarificationAnswer = needsClarification.fallbackAnswer();

        // then
        assertThat(noResult.status()).isEqualTo(SearchResultStatus.NO_RESULT);
        assertThat(noResultAnswer).contains("찾지 못했습니다");
        assertThat(needsClarification.status()).isEqualTo(SearchResultStatus.NEEDS_CLARIFICATION);
        assertThat(clarificationAnswer).contains("범위가 넓어요");
        assertThat(noResult.contextReferences()).isEmpty();
        assertThatThrownBy(ready::fallbackAnswer).isInstanceOf(IllegalStateException.class);
    }

    private static SearchChunk chunk(
            int chunkIndex,
            String content
    ) {
        return SearchChunk.retrieved(
                1L,
                201L,
                301L,
                chunkIndex,
                "문서 " + chunkIndex,
                "https://notion.test/" + chunkIndex,
                Instant.parse("2026-09-01T00:00:00Z"),
                content,
                0.9
        );
    }
}
