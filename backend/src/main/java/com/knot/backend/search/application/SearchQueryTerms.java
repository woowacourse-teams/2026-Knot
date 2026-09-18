package com.knot.backend.search.application;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class SearchQueryTerms {
    private static final Set<String> STOP_WORDS = Set.of(
            "우리",
            "뭐",
            "뭐였지",
            "정했지",
            "했더라",
            "알려줘",
            "알려",
            "찾아줘",
            "찾아",
            "어떤",
            "어떻게",
            "언제",
            "왜",
            "사용",
            "쓰기로",
            "결정",
            "했어",
            "했나요"
    );

    public List<String> extract(String query) {
        return Arrays.stream(
                query.toLowerCase(Locale.ROOT)
                        .split("[^\\p{L}\\p{N}_-]+")
        )
                .filter(term -> term.length() >= 2)
                .filter(term -> !STOP_WORDS.contains(term))
                .distinct()
                .limit(8)
                .toList();
    }
}
