package com.knot.backend.document.infrastructure.llm;

import com.knot.backend.document.application.dto.result.DocumentGenerationResult;
import com.knot.backend.document.domain.DocumentErrorCode;
import com.knot.backend.document.domain.DocumentException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "knot.llm", name = "enabled", havingValue = "true")
public class DocumentMarkdownValidator {

    private static final List<String> FIXED_SECTIONS = List.of(
            "결정",
            "보류",
            "미결정",
            "할 일"
    );
    private static final Pattern HEADING = Pattern.compile("^ {0,3}##[ \\t]+(.+?)[ \\t]*#*[ \\t]*$");
    private static final Pattern FENCE = Pattern.compile("^ {0,3}(`{3,}|~{3,})(.*)$");
    private static final Pattern LINKS = Pattern.compile(
            "(?i)(?:https?://|mailto:|www\\.|!?\\[[^\\]\\n]*\\]\\s*(?:\\(|\\[)|^ {0,3}\\[[^\\]]+\\]:|<(?:a|img)\\b)",
            Pattern.MULTILINE
    );
    private static final Pattern PLACEHOLDER = Pattern.compile("^(?:없음|해당없음|내용없음)[.!。]?$");
    private static final Pattern BLANK = Pattern.compile("[\\p{javaWhitespace}\\p{Z}]*");

    public void validate(DocumentGenerationResult result) {
        validateLinks(result.title());
        validateLinks(result.summary());
        validateLinks(result.content());
        Map<String, StringBuilder> sections = readSections(result.content());
        validateSummarySection(sections);
        validateSectionOrder(sections);
        sections.values()
                .forEach(body -> validateSectionBody(body.toString()));
    }

    private Map<String, StringBuilder> readSections(String content) {
        if (!content.startsWith("## 핵심 요약")) {
            throw invalidResponse();
        }
        Map<String, StringBuilder> sections = new LinkedHashMap<>();
        StringBuilder body = null;
        String fence = null;
        for (String line : content.split(
                "\\R",
                -1
        )) {
            Matcher fenceMatcher = FENCE.matcher(line);
            if (fenceMatcher.matches()) {
                fence = updateFence(
                        fence,
                        fenceMatcher
                );
            } else if (fence == null) {
                Matcher heading = HEADING.matcher(line);
                if (heading.matches()) {
                    String name = heading.group(1)
                            .strip();
                    if (sections.containsKey(name)) {
                        throw invalidResponse();
                    }
                    body = new StringBuilder();
                    sections.put(
                            name,
                            body
                    );
                    continue;
                }
                if (line.matches("^ {0,3}#[ \\t]+.*")) {
                    throw invalidResponse();
                }
            }
            if (body == null) {
                if (!BLANK.matcher(line)
                        .matches()) {
                    throw invalidResponse();
                }
            } else {
                body.append(line)
                        .append('\n');
            }
        }
        if (fence != null) {
            throw invalidResponse();
        }
        return sections;
    }

    private String updateFence(
            String fence,
            Matcher matcher
    ) {
        String marker = matcher.group(1);
        if (fence == null) {
            return marker;
        }
        if (marker.charAt(0) == fence.charAt(0) && marker.length() >= fence.length() && matcher.group(2)
                .isBlank()) {
            return null;
        }
        return fence;
    }

    private void validateSummarySection(Map<String, StringBuilder> sections) {
        if (sections.isEmpty() || !sections.keySet()
                .iterator()
                .next()
                .equals("핵심 요약")) {
            throw invalidResponse();
        }
    }

    private void validateSectionOrder(Map<String, StringBuilder> sections) {
        int previous = -1;
        boolean additionalSectionStarted = false;
        for (String name : sections.keySet()) {
            if (name.equals("핵심 요약")) {
                continue;
            }
            int index = FIXED_SECTIONS.indexOf(name);
            if (index < 0) {
                additionalSectionStarted = true;
                continue;
            }
            if (additionalSectionStarted || index <= previous) {
                throw invalidResponse();
            }
            previous = index;
        }
    }

    private void validateSectionBody(String body) {
        if (body.lines()
                .noneMatch(this::isBodyLine)) {
            throw invalidResponse();
        }
        boolean onlyPlaceholders = body.lines()
                .filter(this::isBodyLine)
                .map(String::strip)
                .map(
                        line -> line.replaceFirst(
                                "^(?:[-*+] |[0-9]+[.)] )",
                                ""
                        )
                )
                .map(
                        line -> line.replaceAll(
                                "[()*_`\\p{Z}]",
                                ""
                        )
                )
                .allMatch(
                        line -> PLACEHOLDER.matcher(line)
                                .matches()
                );
        if (onlyPlaceholders) {
            throw invalidResponse();
        }
    }

    private boolean isBodyLine(String line) {
        if (BLANK.matcher(line)
                .matches()
                || FENCE.matcher(line)
                        .matches()) {
            return false;
        }
        return !line.matches("^ {0,3}#{1,6}[ \\t]+.*|^\\s*[-*+]\\s*$");
    }

    private void validateLinks(String value) {
        if (value != null && LINKS.matcher(value)
                .find()) {
            throw invalidResponse();
        }
    }

    private DocumentException invalidResponse() {
        return new DocumentException(DocumentErrorCode.INVALID_DOCUMENT_GENERATION_RESPONSE);
    }
}
