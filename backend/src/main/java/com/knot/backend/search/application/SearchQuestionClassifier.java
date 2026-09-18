package com.knot.backend.search.application;

import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class SearchQuestionClassifier {
    private static final List<String> BROAD_PHRASES = List.of(
            "어떻게 진행",
            "전체적으로",
            "전반적으로",
            "프로젝트 현황",
            "프로젝트 요약",
            "전체 요약",
            "전부 알려"
    );

    public boolean isBroad(String query) {
        String normalized = query.toLowerCase(Locale.ROOT);
        return BROAD_PHRASES.stream()
                .anyMatch(normalized::contains);
    }
}
