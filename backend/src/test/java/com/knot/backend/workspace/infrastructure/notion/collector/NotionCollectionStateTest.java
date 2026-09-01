package com.knot.backend.workspace.infrastructure.notion.collector;

import static org.assertj.core.api.Assertions.assertThat;

import com.knot.backend.workspace.application.dto.result.CollectedNotionPage;
import com.knot.backend.workspace.infrastructure.notion.collector.NotionCollectionState.NotionObjectType;
import com.knot.backend.workspace.infrastructure.notion.collector.NotionCollectionState.PlacementPriority;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NotionCollectionStateTest {

    @DisplayName("중복된 Data Source 행은 첫 번째 query 순서를 유지한다")
    @Test
    void toResult_success_preservesFirstStructuralOrderForDuplicateRow() {
        // given
        NotionCollectionState state = new NotionCollectionState();
        state.setContent(
                "data-source",
                NotionObjectType.DATA_SOURCE,
                "Data Source",
                "",
                "https://notion.so/data-source"
        );
        addPage(
                state,
                "page-first",
                0
        );
        addPage(
                state,
                "page-second",
                1
        );
        state.place(
                "page-first",
                NotionObjectType.PAGE,
                "data-source",
                2,
                PlacementPriority.STRUCTURAL
        );

        // when
        List<CollectedNotionPage> pages = state.toResult();

        // then
        assertThat(pages).extracting(CollectedNotionPage::notionPageId)
                .containsExactly(
                        "data-source",
                        "page-first",
                        "page-second"
                );
    }

    private void addPage(
            NotionCollectionState state,
            String pageId,
            long order
    ) {
        state.place(
                pageId,
                NotionObjectType.PAGE,
                "data-source",
                order,
                PlacementPriority.STRUCTURAL
        );
        state.setContent(
                pageId,
                NotionObjectType.PAGE,
                pageId,
                "",
                "https://notion.so/" + pageId
        );
    }
}
