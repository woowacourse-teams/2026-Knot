package com.knot.backend.search.application;

import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MarkdownChunker {
    private static final int MIN_BOUNDARY_LENGTH = 80;
    private final SearchProperties properties;

    public List<MarkdownChunk> chunk(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return List.of();
        }
        String normalized = normalize(markdown);
        List<MarkdownChunk> chunks = new ArrayList<>();
        for (String section : sections(normalized)) {
            appendSection(
                    section,
                    chunks
            );
        }
        return List.copyOf(chunks);
    }

    private String normalize(String markdown) {
        return markdown.replace(
                "\r\n",
                "\n"
        )
                .replace(
                        '\r',
                        '\n'
                )
                .trim();
    }

    private List<String> sections(String markdown) {
        return List.of(
                markdown.split(
                        "(?m)(?=^#{1,6}\\s)",
                        -1
                )
        );
    }

    private void appendSection(
            String section,
            List<MarkdownChunk> chunks
    ) {
        String remaining = section.trim();
        while (!remaining.isEmpty()) {
            if (remaining.length() <= properties.chunkSize()) {
                addChunk(
                        remaining,
                        chunks
                );
                return;
            }
            int end = preferredEnd(remaining);
            String part = remaining.substring(
                    0,
                    end
            )
                    .trim();
            if (!part.isEmpty()) {
                addChunk(
                        part,
                        chunks
                );
            }
            int nextStart = Math.max(
                    end - properties.chunkOverlap(),
                    1
            );
            if (nextStart >= remaining.length()) {
                return;
            }
            remaining = remaining.substring(nextStart)
                    .trim();
        }
    }

    private int preferredEnd(String text) {
        int limit = properties.chunkSize();
        int boundary = Math.max(
                text.lastIndexOf(
                        '\n',
                        limit
                ),
                text.lastIndexOf(
                        ' ',
                        limit
                )
        );
        if (boundary >= MIN_BOUNDARY_LENGTH) {
            return boundary;
        }
        return limit;
    }

    private void addChunk(
            String content,
            List<MarkdownChunk> chunks
    ) {
        chunks.add(
                new MarkdownChunk(
                        chunks.size(),
                        content
                )
        );
    }
}
