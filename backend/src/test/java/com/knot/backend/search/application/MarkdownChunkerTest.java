package com.knot.backend.search.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MarkdownChunkerTest {

    @Test
    @DisplayName("긴 Markdown을 설정된 크기와 겹침으로 나누고 원문 순서를 유지한다")
    void chunk_success_preservesOrderWithOverlap() {
        // given
        SearchProperties properties = new SearchProperties(
                30,
                5,
                10,
                3,
                1000,
                64,
                0.35
        );
        MarkdownChunker chunker = new MarkdownChunker(properties);

        // when
        List<MarkdownChunk> chunks = chunker.chunk("# 제목\n가나다라마바사아자차카타파하거너더러머버서어저처커터퍼허");

        // then
        assertThat(chunks).hasSizeGreaterThan(1)
                .extracting(MarkdownChunk::index)
                .containsExactly(
                        0,
                        1
                );
        assertThat(
                chunks.get(0)
                        .content()
        ).startsWith("# 제목");
        assertThat(
                chunks.get(0)
                        .content()
        ).contains(
                chunks.get(1)
                        .content()
                        .substring(
                                0,
                                5
                        )
        );
    }

    @Test
    @DisplayName("빈 Markdown은 색인할 청크를 만들지 않는다")
    void chunk_success_emptyMarkdown() {
        // when
        List<MarkdownChunk> chunks = new MarkdownChunker(
                new SearchProperties(
                        100,
                        10,
                        10,
                        3,
                        1000,
                        64,
                        0.35
                )
        ).chunk("  ");

        // then
        assertThat(chunks).isEmpty();
    }
}
