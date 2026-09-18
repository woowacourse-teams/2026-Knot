package com.knot.backend.workspace.infrastructure.notion.collector;

import static com.knot.backend.workspace.infrastructure.notion.collector.NotionJson.invalidResponse;

import com.knot.backend.workspace.application.dto.result.CollectedPage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

final class NotionCollectionState {
    private final Map<String, Draft> drafts = new LinkedHashMap<>();
    private final Map<String, CollectionProgress> pageProgress = new HashMap<>();
    private final Map<String, CollectionProgress> dataSourceProgress = new HashMap<>();
    private final Set<String> seenBlockIds = new HashSet<>();
    private final Map<String, ParentPlacement> databasePlacements = new HashMap<>();
    private final Map<String, Integer> skippedBlockCounts = new HashMap<>();
    private long nextOrder;

    long nextOrder() {
        return nextOrder++;
    }

    void place(
            String id,
            NotionObjectType objectType,
            String parentId,
            long order,
            PlacementPriority priority
    ) {
        draft(
                id,
                objectType
        ).place(
                parentId,
                order,
                priority
        );
    }

    void setContent(
            String id,
            NotionObjectType objectType,
            String title,
            String markdownContent,
            String notionUrl
    ) {
        draft(
                id,
                objectType
        ).setContent(
                title,
                markdownContent,
                notionUrl
        );
    }

    void setMarkdown(
            String pageId,
            String markdownContent
    ) {
        draft(
                pageId,
                NotionObjectType.PAGE
        ).markdownContent = Objects.requireNonNull(
                markdownContent,
                "markdownContent"
        );
    }

    void setPageProperties(
            String pageId,
            Map<String, Integer> complexPropertyCounts
    ) {
        draft(
                pageId,
                NotionObjectType.PAGE
        ).complexPropertyCounts = Map.copyOf(complexPropertyCounts);
    }

    void setDatabaseContainerId(
            String id,
            NotionObjectType objectType,
            String databaseContainerId
    ) {
        draft(
                id,
                objectType
        ).databaseContainerId = databaseContainerId;
    }

    void markDataSourceRow(String pageId) {
        draft(
                pageId,
                NotionObjectType.PAGE
        ).dataSourceRow = true;
    }

    boolean startPage(String id) {
        return start(
                pageProgress,
                id
        );
    }

    boolean startDataSource(String id) {
        return start(
                dataSourceProgress,
                id
        );
    }

    void finishPage(String id) {
        pageProgress.put(
                id,
                CollectionProgress.COMPLETE
        );
    }

    void finishDataSource(String id) {
        dataSourceProgress.put(
                id,
                CollectionProgress.COMPLETE
        );
    }

    boolean markBlockSeen(String blockId) {
        return seenBlockIds.add(blockId);
    }

    void mergeSkippedBlocks(Map<String, Integer> counts) {
        counts.forEach(
                (
                        type,
                        count
                ) -> skippedBlockCounts.merge(
                        type,
                        count,
                        Integer::sum
                )
        );
    }

    void placeDatabase(
            String databaseId,
            String parentPageId,
            long order
    ) {
        databasePlacements.putIfAbsent(
                databaseId,
                new ParentPlacement(
                        parentPageId,
                        order
                )
        );
    }

    void resolveDataSourceParents() {
        for (Draft draft : drafts.values()) {
            if (draft.databaseContainerId == null) {
                continue;
            }
            ParentPlacement placement = databasePlacements.get(draft.databaseContainerId);
            if (placement != null) {
                draft.place(
                        placement.parentId(),
                        placement.order(),
                        PlacementPriority.STRUCTURAL
                );
            }
        }
    }

    Map<String, Integer> skippedBlockCounts() {
        return Map.copyOf(skippedBlockCounts);
    }

    Map<String, Integer> skippedPropertyCounts() {
        Map<String, Integer> counts = new HashMap<>();
        for (Draft draft : drafts.values()) {
            if (!draft.dataSourceRow) {
                continue;
            }
            draft.complexPropertyCounts.forEach(
                    (
                            type,
                            count
                    ) -> counts.merge(
                            type,
                            count,
                            Integer::sum
                    )
            );
        }
        return counts;
    }

    List<CollectedPage> toResult() {
        validateCompleted();
        normalizeParents();
        Map<String, List<Draft>> childrenByParent = childrenByParent();
        List<Draft> ordered = orderedDrafts(childrenByParent);
        List<CollectedPage> pages = new ArrayList<>();
        for (int position = 0; position < ordered.size(); position++) {
            Draft draft = ordered.get(position);
            pages.add(
                    new CollectedPage(
                            draft.id,
                            draft.parentId,
                            draft.title,
                            draft.markdownContent,
                            position,
                            draft.notionUrl
                    )
            );
        }
        return pages;
    }

    private Draft draft(
            String id,
            NotionObjectType objectType
    ) {
        Draft existing = drafts.get(id);
        if (existing != null) {
            if (existing.objectType != objectType) {
                throw invalidResponse();
            }
            return existing;
        }
        Draft created = new Draft(
                id,
                objectType,
                nextOrder()
        );
        drafts.put(
                id,
                created
        );
        return created;
    }

    private boolean start(
            Map<String, CollectionProgress> progress,
            String id
    ) {
        CollectionProgress existing = progress.putIfAbsent(
                id,
                CollectionProgress.IN_PROGRESS
        );
        return existing == null;
    }

    private void validateCompleted() {
        if (pageProgress.containsValue(CollectionProgress.IN_PROGRESS)
                || dataSourceProgress.containsValue(CollectionProgress.IN_PROGRESS)) {
            throw invalidResponse();
        }
        for (Draft draft : drafts.values()) {
            if (draft.notionUrl == null) {
                throw invalidResponse();
            }
        }
    }

    private void normalizeParents() {
        for (Draft draft : drafts.values()) {
            if (draft.id.equals(draft.parentId)) {
                throw invalidResponse();
            }
            if (draft.parentId != null && !drafts.containsKey(draft.parentId)) {
                draft.parentId = null;
            }
        }
    }

    private Map<String, List<Draft>> childrenByParent() {
        Map<String, List<Draft>> children = new HashMap<>();
        for (Draft draft : drafts.values()) {
            children.computeIfAbsent(
                    draft.parentId,
                    ignored -> new ArrayList<>()
            )
                    .add(draft);
        }
        Comparator<Draft> orderComparator = Comparator.comparingLong((Draft draft) -> draft.order)
                .thenComparingLong(draft -> draft.discoveryOrder)
                .thenComparing(draft -> draft.id);
        children.values()
                .forEach(values -> values.sort(orderComparator));
        return children;
    }

    private List<Draft> orderedDrafts(Map<String, List<Draft>> childrenByParent) {
        List<Draft> ordered = new ArrayList<>();
        Set<String> visiting = new LinkedHashSet<>();
        Set<String> visited = new HashSet<>();
        appendChildren(
                null,
                childrenByParent,
                ordered,
                visiting,
                visited
        );
        if (visited.size() != drafts.size()) {
            throw invalidResponse();
        }
        return ordered;
    }

    private void appendChildren(
            String parentId,
            Map<String, List<Draft>> childrenByParent,
            List<Draft> ordered,
            Set<String> visiting,
            Set<String> visited
    ) {
        for (Draft child : childrenByParent.getOrDefault(
                parentId,
                List.of()
        )) {
            if (!visiting.add(child.id)) {
                throw invalidResponse();
            }
            if (visited.add(child.id)) {
                ordered.add(child);
                appendChildren(
                        child.id,
                        childrenByParent,
                        ordered,
                        visiting,
                        visited
                );
            }
            visiting.remove(child.id);
        }
    }

    enum NotionObjectType {
        PAGE,
        DATA_SOURCE,
    }

    enum PlacementPriority {
        RESPONSE_PARENT,
        STRUCTURAL,
    }

    private enum CollectionProgress {
        IN_PROGRESS,
        COMPLETE,
    }

    private record ParentPlacement(
            String parentId,
            long order
    ) {
    }

    private static final class Draft {
        private final String id;
        private final NotionObjectType objectType;
        private final long discoveryOrder;
        private String parentId;
        private long order;
        private PlacementPriority placementPriority;
        private String title = "";
        private String markdownContent = "";
        private String notionUrl;
        private String databaseContainerId;
        private boolean dataSourceRow;
        private Map<String, Integer> complexPropertyCounts = Map.of();

        private Draft(
                String id,
                NotionObjectType objectType,
                long discoveryOrder
        ) {
            this.id = id;
            this.objectType = objectType;
            this.discoveryOrder = discoveryOrder;
            this.order = discoveryOrder;
        }

        private void place(
                String candidateParentId,
                long candidateOrder,
                PlacementPriority candidatePriority
        ) {
            if (candidateParentId == null) {
                return;
            }
            if (placementPriority != null && candidatePriority.ordinal() <= placementPriority.ordinal()) {
                return;
            }
            parentId = candidateParentId;
            order = candidateOrder;
            placementPriority = candidatePriority;
        }

        private void setContent(
                String title,
                String markdownContent,
                String notionUrl
        ) {
            this.title = Objects.requireNonNull(
                    title,
                    "title"
            );
            this.markdownContent = Objects.requireNonNull(
                    markdownContent,
                    "markdownContent"
            );
            this.notionUrl = Objects.requireNonNull(
                    notionUrl,
                    "notionUrl"
            );
        }
    }
}
