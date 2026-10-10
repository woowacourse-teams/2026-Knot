package com.knot.backend.document.domain;

import java.util.regex.Pattern;

public final class DocumentText {
    private static final Pattern WHITESPACE = Pattern.compile("[\\p{javaWhitespace}\\p{Z}]+");

    private DocumentText() {}

    public static boolean isBlank(String value) {
        if (value == null || value.isEmpty()) {
            return true;
        }
        return WHITESPACE.matcher(value)
                .matches();
    }

    public static String normalizeWhitespace(String value) {
        return WHITESPACE.matcher(value)
                .replaceAll(" ")
                .strip();
    }
}
