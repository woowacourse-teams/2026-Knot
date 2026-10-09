package com.knot.backend.document.infrastructure.llm;

import com.knot.backend.document.domain.DocumentErrorCode;
import com.knot.backend.document.domain.DocumentException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class DocumentMarkdownSectionReader {
    private static final Pattern HEADING = Pattern.compile("^ {0,3}##[ \\t]+(.+?)[ \\t]*#*[ \\t]*$");
    private static final Pattern FENCE = Pattern.compile("^ {0,3}(`{3,}|~{3,})(.*)$");
    private static final Pattern BLANK = Pattern.compile("[\\p{javaWhitespace}\\p{Z}]*");

    private final Map<String, StringBuilder> sections = new LinkedHashMap<>();
    private StringBuilder body;
    private String fence;

    Map<String, StringBuilder> read(String content) {
        validateOpeningHeading(content);
        for (String line : content.split(
                "\\R",
                -1
        )) {
            readLine(line);
        }
        validateClosedFence();
        return sections;
    }

    private void readLine(String line) {
        Matcher marker = FENCE.matcher(line);
        if (marker.matches()) {
            updateFence(marker);
            appendBody(line);
            return;
        }
        if (fence != null) {
            appendBody(line);
            return;
        }
        Matcher heading = HEADING.matcher(line);
        if (heading.matches()) {
            startSection(heading);
            return;
        }
        validateBodyHeading(line);
        appendBody(line);
    }

    private void startSection(Matcher heading) {
        String name = heading.group(1);
        name = name.strip();
        validateUniqueSection(name);
        body = new StringBuilder();
        sections.put(
                name,
                body
        );
    }

    private void updateFence(Matcher matcher) {
        String marker = matcher.group(1);
        if (fence == null) {
            fence = marker;
            return;
        }
        String trailing = matcher.group(2);
        if (isClosingFence(
                marker,
                trailing
        )) {
            fence = null;
        }
    }

    private boolean isClosingFence(
            String marker,
            String trailing
    ) {
        char markerType = marker.charAt(0);
        char fenceType = fence.charAt(0);
        if (markerType != fenceType) {
            return false;
        }
        int fenceLength = fence.length();
        if (marker.length() < fenceLength) {
            return false;
        }
        return trailing.isBlank();
    }

    private void appendBody(String line) {
        if (body == null) {
            validatePreamble(line);
            return;
        }
        body.append(line);
        body.append('\n');
    }

    private void validateOpeningHeading(String content) {
        if (!content.startsWith("## 핵심 요약")) {
            throw invalidResponse();
        }
    }

    private void validateUniqueSection(String name) {
        if (sections.containsKey(name)) {
            throw invalidResponse();
        }
    }

    private void validateBodyHeading(String line) {
        if (line.matches("^ {0,3}#[ \\t]+.*")) {
            throw invalidResponse();
        }
    }

    private void validatePreamble(String line) {
        Matcher blank = BLANK.matcher(line);
        if (!blank.matches()) {
            throw invalidResponse();
        }
    }

    private void validateClosedFence() {
        if (fence != null) {
            throw invalidResponse();
        }
    }

    private DocumentException invalidResponse() {
        return new DocumentException(DocumentErrorCode.INVALID_DOCUMENT_GENERATION_RESPONSE);
    }
}
