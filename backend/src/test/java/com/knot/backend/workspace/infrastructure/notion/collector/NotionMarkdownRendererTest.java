package com.knot.backend.workspace.infrastructure.notion.collector;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class NotionMarkdownRendererTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final NotionMarkdownRenderer renderer = new NotionMarkdownRenderer();

    @DisplayName("Page title Rich Text fixture를 순서대로 plain text로 변환한다")
    @Test
    void plainText_success_goldenFixture() throws Exception {
        // given
        JsonNode page = readJson("/notion/collector/page-rich-text.json");
        JsonNode title = page.get("properties")
                .get("Name")
                .get("title");

        // when
        String plainText = renderer.plainText(title);

        // then
        assertThat(plainText).isEqualTo("Knot Import");
    }

    @DisplayName("지원하는 text block fixture를 Markdown golden file로 변환하고 지원하지 않는 type을 집계한다")
    @Test
    void render_success_goldenFixture() throws Exception {
        // given
        JsonNode fixture = readJson("/notion/collector/markdown-blocks.json");
        List<NotionBlock> blocks = blocks(fixture);

        // when
        RenderedNotionMarkdown rendered = renderer.render(blocks);

        // then
        assertThat(rendered.markdown()).isEqualTo(readText("/notion/collector/expected.md").stripTrailing());
        assertThat(rendered.skippedBlockCounts()).containsExactlyEntriesOf(
                java.util.Map.of(
                        "image",
                        1
                )
        );
        assertThat(rendered.markdown()).doesNotContain("raw-secret.png");
    }

    @DisplayName("ordered list 자식을 marker 폭으로 들여쓰고 일반 문단의 list marker를 escape한다")
    @Test
    void render_success_preservesParagraphMarkersAndOrderedListNesting() throws Exception {
        // given
        JsonNode fixture = readJson("/notion/collector/markdown-boundaries.json");

        // when
        RenderedNotionMarkdown rendered = renderer.render(blocks(fixture));

        // then
        assertThat(rendered.markdown()).isEqualTo(readText("/notion/collector/expected-boundaries.md").stripTrailing());
    }

    private List<NotionBlock> blocks(JsonNode values) {
        List<NotionBlock> blocks = new ArrayList<>();
        for (JsonNode value : values) {
            JsonNode children = value.get("children");
            blocks.add(
                    new NotionBlock(
                            value,
                            children == null ? List.of() : blocks(children)
                    )
            );
        }
        return blocks;
    }

    private JsonNode readJson(String path) throws IOException, JacksonException {
        try (InputStream input = getClass().getResourceAsStream(path)) {
            if (input == null) {
                throw new IOException(path);
            }
            return objectMapper.readTree(input);
        }
    }

    private String readText(String path) throws IOException {
        try (InputStream input = getClass().getResourceAsStream(path)) {
            if (input == null) {
                throw new IOException(path);
            }
            return new String(
                    input.readAllBytes(),
                    StandardCharsets.UTF_8
            );
        }
    }
}
