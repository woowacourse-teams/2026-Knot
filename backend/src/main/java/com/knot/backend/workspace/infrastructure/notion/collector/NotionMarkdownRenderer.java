package com.knot.backend.workspace.infrastructure.notion.collector;

import com.knot.backend.workspace.application.ContentCollectionException;
import com.knot.backend.workspace.application.ContentCollectionFailureType;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import tools.jackson.databind.JsonNode;

final class NotionMarkdownRenderer {
    private static final Set<String> LIST_TYPES = listTypes();
    private static final Set<String> STRUCTURAL_TYPES = structuralTypes();
    private static final Pattern SAFE_CODE_LANGUAGE = Pattern.compile("[A-Za-z0-9_+#.-]{1,32}");

    RenderedNotionMarkdown render(List<NotionBlock> blocks) {
        Map<String, Integer> skippedBlockCounts = new HashMap<>();
        String markdown = renderSiblings(
                blocks,
                0,
                skippedBlockCounts
        ).stripTrailing();
        return new RenderedNotionMarkdown(
                markdown,
                skippedBlockCounts
        );
    }

    String plainText(JsonNode richTexts) {
        requireArray(richTexts);
        StringBuilder plainText = new StringBuilder();
        for (JsonNode richText : richTexts) {
            plainText.append(
                    requiredString(
                            richText,
                            "plain_text"
                    )
            );
        }
        return plainText.toString();
    }

    private String renderSiblings(
            List<NotionBlock> blocks,
            int indentation,
            Map<String, Integer> skippedBlockCounts
    ) {
        List<RenderedBlock> renderedBlocks = new ArrayList<>();
        for (NotionBlock block : blocks) {
            RenderedBlock renderedBlock = renderBlock(
                    block,
                    indentation,
                    skippedBlockCounts
            );
            if (!renderedBlock.markdown()
                    .isBlank()) {
                renderedBlocks.add(renderedBlock);
            }
        }

        StringBuilder markdown = new StringBuilder();
        RenderedBlock previous = null;
        for (RenderedBlock current : renderedBlocks) {
            if (previous != null) {
                markdown.append(previous.listItem() && current.listItem() ? '\n' : "\n\n");
            }
            markdown.append(current.markdown());
            previous = current;
        }
        return markdown.toString();
    }

    private RenderedBlock renderBlock(
            NotionBlock block,
            int indentation,
            Map<String, Integer> skippedBlockCounts
    ) {
        String type = requiredString(
                block.value(),
                "type"
        );
        String ownMarkdown = renderOwn(
                block.value(),
                type
        );
        if (ownMarkdown == null && !STRUCTURAL_TYPES.contains(type)) {
            skippedBlockCounts.merge(
                    type,
                    1,
                    Integer::sum
            );
        }

        boolean listItem = LIST_TYPES.contains(type);
        int childIndentation = indentation + listMarkerWidth(type);
        String childMarkdown = renderSiblings(
                block.children(),
                childIndentation,
                skippedBlockCounts
        );
        StringBuilder markdown = new StringBuilder();
        if (ownMarkdown != null && !ownMarkdown.isBlank()) {
            markdown.append(
                    indent(
                            ownMarkdown,
                            indentation
                    )
            );
        }
        if (!childMarkdown.isBlank()) {
            if (!markdown.isEmpty()) {
                markdown.append('\n');
            }
            markdown.append(childMarkdown);
        }
        return new RenderedBlock(
                markdown.toString(),
                listItem
        );
    }

    private String renderOwn(
            JsonNode block,
            String type
    ) {
        JsonNode content = block.get(type);
        return switch (type) {
            case "paragraph" -> paragraph(content);
            case "heading_1" -> heading(
                    "# ",
                    content
            );
            case "heading_2" -> heading(
                    "## ",
                    content
            );
            case "heading_3" -> heading(
                    "### ",
                    content
            );
            case "heading_4" -> heading(
                    "#### ",
                    content
            );
            case "bulleted_list_item" -> "- " + richTextContent(content);
            case "numbered_list_item" -> "1. " + richTextContent(content);
            case "to_do" -> "- [" + (optionalBoolean(
                    content,
                    "checked"
            ) ? "x" : " ") + "] " + richTextContent(content);
            case "toggle" -> "- " + richTextContent(content);
            case "quote" -> prefixLines(
                    "> ",
                    richTextContent(content)
            );
            case "callout" -> callout(content);
            case "code" -> code(content);
            case "equation" -> equation(content);
            case "divider" -> "---";
            case "bookmark" -> bookmark(content);
            case "child_page", "child_database" -> "";
            default -> null;
        };
    }

    private String heading(
            String prefix,
            JsonNode content
    ) {
        return prefix + richTextContent(content);
    }

    private String paragraph(JsonNode content) {
        return escapeParagraphLineStarts(richTextContent(content));
    }

    private String richTextContent(JsonNode content) {
        requireObject(content);
        return richText(content.get("rich_text"));
    }

    private String richText(JsonNode richTexts) {
        requireArray(richTexts);
        StringBuilder markdown = new StringBuilder();
        for (JsonNode richText : richTexts) {
            String plainText = requiredString(
                    richText,
                    "plain_text"
            );
            JsonNode annotations = richText.get("annotations");
            boolean code = false;
            if (annotations != null && !annotations.isNull()) {
                requireObject(annotations);
                code = optionalBoolean(
                        annotations,
                        "code"
                );
            }
            String text = code ? inlineCode(plainText) : escapeMarkdown(plainText);
            if (annotations != null && !annotations.isNull()) {
                if (optionalBoolean(
                        annotations,
                        "bold"
                )) {
                    text = "**" + text + "**";
                }
                if (optionalBoolean(
                        annotations,
                        "italic"
                )) {
                    text = "_" + text + "_";
                }
                if (optionalBoolean(
                        annotations,
                        "strikethrough"
                )) {
                    text = "~~" + text + "~~";
                }
            }
            String href = optionalString(
                    richText,
                    "href"
            );
            String destination = markdownLinkDestination(href);
            if (destination != null) {
                text = "[" + text + "](" + destination + ")";
            }
            markdown.append(text);
        }
        return markdown.toString();
    }

    private String inlineCode(String text) {
        int firstContent = 0;
        while (firstContent < text.length() && Character.isWhitespace(text.charAt(firstContent))) {
            firstContent++;
        }
        int afterContent = text.length();
        while (afterContent > firstContent && Character.isWhitespace(text.charAt(afterContent - 1))) {
            afterContent--;
        }
        if (firstContent == text.length()) {
            return text;
        }
        String content = text.substring(
                firstContent,
                afterContent
        );
        String delimiter = backtickFence(
                content,
                1
        );
        String wrapped = delimiter + content + delimiter;
        if (content.contains("`")) {
            wrapped = delimiter + " " + content + " " + delimiter;
        }
        return text.substring(
                0,
                firstContent
        ) + wrapped + text.substring(afterContent);
    }

    private String callout(JsonNode content) {
        requireObject(content);
        String icon = "";
        JsonNode iconNode = content.get("icon");
        if (iconNode != null && iconNode.isObject() && "emoji".equals(
                optionalString(
                        iconNode,
                        "type"
                )
        )) {
            icon = optionalString(
                    iconNode,
                    "emoji"
            );
        }
        String text = richText(content.get("rich_text"));
        String body = icon == null || icon.isBlank() ? text : icon + " " + text;
        return prefixLines(
                "> ",
                body
        );
    }

    private String code(JsonNode content) {
        requireObject(content);
        String code = plainText(content.get("rich_text"));
        String rawLanguage = optionalString(
                content,
                "language"
        );
        String language = rawLanguage != null && SAFE_CODE_LANGUAGE.matcher(rawLanguage)
                .matches() ? rawLanguage : "";
        String fence = backtickFence(
                code,
                3
        );
        return fence + language + '\n' + code + '\n' + fence;
    }

    private String equation(JsonNode content) {
        requireObject(content);
        return "$$\n" + requiredString(
                content,
                "expression"
        ) + "\n$$";
    }

    private String bookmark(JsonNode content) {
        requireObject(content);
        String url = requiredString(
                content,
                "url"
        );
        String destination = markdownLinkDestination(url);
        if (destination == null) {
            return null;
        }
        JsonNode caption = content.get("caption");
        String label = caption == null || caption.isNull() ? "" : plainText(caption);
        if (label.isBlank()) {
            label = url;
        }
        return "[" + escapeMarkdown(label) + "](" + destination + ")";
    }

    private String prefixLines(
            String prefix,
            String value
    ) {
        return prefix + value.replace(
                "\n",
                "\n" + prefix
        );
    }

    private String indent(
            String value,
            int size
    ) {
        if (size == 0) {
            return value;
        }
        String indentation = " ".repeat(size);
        return indentation + value.replace(
                "\n",
                "\n" + indentation
        );
    }

    private int listMarkerWidth(String type) {
        return switch (type) {
            case "numbered_list_item" -> 3;
            case "bulleted_list_item", "to_do", "toggle" -> 2;
            default -> 0;
        };
    }

    private String escapeParagraphLineStarts(String value) {
        StringBuilder escaped = new StringBuilder(value.length());
        int lineStart = 0;
        for (int index = 0; index <= value.length(); index++) {
            if (index == value.length() || value.charAt(index) == '\n') {
                escaped.append(
                        escapeParagraphLine(
                                value.substring(
                                        lineStart,
                                        index
                                )
                        )
                );
                if (index < value.length()) {
                    escaped.append('\n');
                }
                lineStart = index + 1;
            }
        }
        return escaped.toString();
    }

    private String escapeParagraphLine(String line) {
        int markerStart = 0;
        while (markerStart < line.length() && markerStart < 3 && line.charAt(markerStart) == ' ') {
            markerStart++;
        }
        if (markerStart == line.length()) {
            return line;
        }

        String content = line.substring(markerStart);
        if (isDashThematicBreak(content)) {
            return insertEscape(
                    line,
                    markerStart
            );
        }
        char first = content.charAt(0);
        if ((first == '-' || first == '+') && isMarkerBoundary(
                content,
                1
        )) {
            return insertEscape(
                    line,
                    markerStart
            );
        }

        int digits = 0;
        while (digits < content.length() && digits < 9 && Character.isDigit(content.charAt(digits))) {
            digits++;
        }
        if (digits > 0 && digits < content.length() && (content.charAt(digits) == '.' || content.charAt(digits) == ')')
                && isMarkerBoundary(
                        content,
                        digits + 1
                )) {
            return insertEscape(
                    line,
                    markerStart + digits
            );
        }
        return line;
    }

    private boolean isDashThematicBreak(String content) {
        int dashCount = 0;
        for (int index = 0; index < content.length(); index++) {
            char character = content.charAt(index);
            if (character == '-') {
                dashCount++;
            } else if (character != ' ' && character != '\t') {
                return false;
            }
        }
        return dashCount >= 3;
    }

    private boolean isMarkerBoundary(
            String content,
            int boundary
    ) {
        return boundary == content.length() || Character.isWhitespace(content.charAt(boundary));
    }

    private String insertEscape(
            String value,
            int index
    ) {
        return value.substring(
                0,
                index
        ) + '\\' + value.substring(index);
    }

    private String escapeMarkdown(String value) {
        return value.replace(
                "\\",
                "\\\\"
        )
                .replace(
                        "*",
                        "\\*"
                )
                .replace(
                        "_",
                        "\\_"
                )
                .replace(
                        "`",
                        "\\`"
                )
                .replace(
                        "~",
                        "\\~"
                )
                .replace(
                        "[",
                        "\\["
                )
                .replace(
                        "]",
                        "\\]"
                )
                .replace(
                        "#",
                        "\\#"
                )
                .replace(
                        "<",
                        "\\<"
                )
                .replace(
                        ">",
                        "\\>"
                );
    }

    private String markdownLinkDestination(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            URI uri = URI.create(value);
            if (!uri.isAbsolute() || !("https".equalsIgnoreCase(uri.getScheme())
                    || "http".equalsIgnoreCase(uri.getScheme()) || "mailto".equalsIgnoreCase(uri.getScheme()))) {
                return null;
            }
            return value.replace(
                    "(",
                    "%28"
            )
                    .replace(
                            ")",
                            "%29"
                    );
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private String backtickFence(
            String value,
            int minimumLength
    ) {
        int longestRun = 0;
        int currentRun = 0;
        for (int index = 0; index < value.length(); index++) {
            if (value.charAt(index) == '`') {
                currentRun++;
                longestRun = Math.max(
                        longestRun,
                        currentRun
                );
            } else {
                currentRun = 0;
            }
        }
        return "`".repeat(
                Math.max(
                        minimumLength,
                        longestRun + 1
                )
        );
    }

    private String requiredString(
            JsonNode node,
            String fieldName
    ) {
        requireObject(node);
        JsonNode value = node.get(fieldName);
        if (value == null || !value.isString()) {
            throw invalidResponse();
        }
        return value.asString();
    }

    private String optionalString(
            JsonNode node,
            String fieldName
    ) {
        if (node == null || !node.isObject()) {
            return null;
        }
        JsonNode value = node.get(fieldName);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isString()) {
            throw invalidResponse();
        }
        return value.asString();
    }

    private boolean optionalBoolean(
            JsonNode node,
            String fieldName
    ) {
        if (node == null || !node.isObject()) {
            return false;
        }
        JsonNode value = node.get(fieldName);
        if (value == null || value.isNull()) {
            return false;
        }
        if (!value.isBoolean()) {
            throw invalidResponse();
        }
        return value.asBoolean();
    }

    private void requireObject(JsonNode node) {
        if (node == null || !node.isObject()) {
            throw invalidResponse();
        }
    }

    private void requireArray(JsonNode node) {
        if (node == null || !node.isArray()) {
            throw invalidResponse();
        }
    }

    private ContentCollectionException invalidResponse() {
        return new ContentCollectionException(ContentCollectionFailureType.INVALID_RESPONSE);
    }

    private static Set<String> listTypes() {
        return Set.of(
                "bulleted_list_item",
                "numbered_list_item",
                "to_do",
                "toggle"
        );
    }

    private static Set<String> structuralTypes() {
        return Set.of(
                "child_page",
                "child_database"
        );
    }

    private record RenderedBlock(
            String markdown,
            boolean listItem
    ) {
    }
}
