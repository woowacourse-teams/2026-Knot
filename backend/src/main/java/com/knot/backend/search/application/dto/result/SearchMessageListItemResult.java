package com.knot.backend.search.application.dto.result;

import com.knot.backend.search.domain.SearchMessage;
import com.knot.backend.search.domain.SearchMessageRole;
import com.knot.backend.search.domain.SearchMessageStatus;
import java.time.Instant;
import java.util.List;

public record SearchMessageListItemResult(
        long id,
        SearchMessageRole role,
        int sequence,
        String content,
        SearchMessageStatus status,
        Instant createdAt,
        List<SearchEvidenceItemResult> evidences
) {

    public SearchMessageListItemResult {
        evidences = List.copyOf(evidences);
    }

    public static SearchMessageListItemResult from(
            SearchMessage message,
            List<SearchEvidenceItemResult> evidences
    ) {
        return new SearchMessageListItemResult(
                message.getId(),
                message.getRole(),
                message.getSequence(),
                message.getContent(),
                message.getStatus(),
                message.getCreatedAt(),
                evidences
        );
    }
}
