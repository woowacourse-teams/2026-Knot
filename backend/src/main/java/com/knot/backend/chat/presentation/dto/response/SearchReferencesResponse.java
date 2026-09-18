package com.knot.backend.chat.presentation.dto.response;

import com.knot.backend.search.domain.SearchReference;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "메시지 검색 출처 목록")
public record SearchReferencesResponse(
        @Schema(description = "관련도 순으로 정렬된 검색 출처 목록") List<SearchReferenceResponse> searchReferences
) {

    public SearchReferencesResponse {
        searchReferences = List.copyOf(searchReferences);
    }

    public static SearchReferencesResponse from(List<SearchReference> references) {
        return new SearchReferencesResponse(
                references.stream()
                        .map(SearchReferenceResponse::from)
                        .toList()
        );
    }
}
