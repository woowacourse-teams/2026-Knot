package com.knot.backend.workspace.infrastructure.notion.collector;

import static com.knot.backend.workspace.infrastructure.notion.collector.NotionJson.invalidResponse;
import static com.knot.backend.workspace.infrastructure.notion.collector.NotionJson.requireObject;
import static com.knot.backend.workspace.infrastructure.notion.collector.NotionJson.requiredNonBlankString;
import static com.knot.backend.workspace.infrastructure.notion.collector.NotionJson.requiredNotionId;

import com.knot.backend.workspace.application.NotionCollectionException;
import com.knot.backend.workspace.application.NotionCollectionFailureType;
import com.knot.backend.workspace.application.NotionContentCollector;
import com.knot.backend.workspace.application.dto.result.CollectedNotionPage;
import com.knot.backend.workspace.application.dto.result.NotionCollectionResult;
import com.knot.backend.workspace.infrastructure.notion.collector.NotionCollectionState.NotionObjectType;
import com.knot.backend.workspace.infrastructure.notion.collector.NotionCollectionState.PlacementPriority;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

public final class HttpNotionContentCollector implements NotionContentCollector {
    static final String NOTION_API_VERSION = NotionApiClient.NOTION_API_VERSION;
    static final int MAX_ATTEMPTS = NotionApiClient.MAX_ATTEMPTS;
    private static final int MAX_ACCESS_CREDENTIAL_LENGTH = 4_096;
    private static final Set<String> COMPLEX_PROPERTY_TYPES = complexPropertyTypes();

    private final NotionApiClient apiClient;
    private final NotionCollectionObserver observer;
    private final NotionMarkdownRenderer markdownRenderer = new NotionMarkdownRenderer();

    HttpNotionContentCollector(
            HttpClient httpClient,
            ObjectMapper objectMapper,
            URI apiBaseUri,
            Duration requestTimeout,
            NotionCollectionObserver observer,
            NotionRetrySleeper sleeper
    ) {
        this.apiClient = new NotionApiClient(
                httpClient,
                objectMapper,
                apiBaseUri,
                requestTimeout,
                sleeper
        );
        this.observer = Objects.requireNonNull(
                observer,
                "observer"
        );
    }

    @Override
    public NotionCollectionResult collect(String accessCredential) {
        validateAccessCredential(accessCredential);
        NotionCollectionState state = new NotionCollectionState();
        try {
            collectSearchResults(
                    apiClient.search(accessCredential),
                    accessCredential,
                    state
            );
            state.resolveDataSourceParents();
            List<CollectedNotionPage> pages = state.toResult();
            return new NotionCollectionResult(pages);
        } finally {
            recordSkippedElements(state);
        }
    }

    private void collectSearchResults(
            List<JsonNode> searchResults,
            String accessCredential,
            NotionCollectionState state
    ) {
        for (JsonNode result : searchResults) {
            String objectType = requiredNonBlankString(
                    result,
                    "object"
            );
            switch (objectType) {
                case "page" -> collectPage(
                        requiredNotionId(
                                result,
                                "id"
                        ),
                        null,
                        state.nextOrder(),
                        PlacementPriority.RESPONSE_PARENT,
                        false,
                        accessCredential,
                        state
                );
                case "data_source" -> collectDataSource(
                        result,
                        accessCredential,
                        state
                );
                default -> throw invalidResponse();
            }
        }
    }

    private void collectDataSource(
            JsonNode dataSource,
            String accessCredential,
            NotionCollectionState state
    ) {
        String dataSourceId = requiredNotionId(
                dataSource,
                "id"
        );
        if (!state.startDataSource(dataSourceId)) {
            return;
        }
        JsonNode fullDataSource = fullDataSource(
                dataSource,
                dataSourceId,
                accessCredential
        );
        state.setContent(
                dataSourceId,
                NotionObjectType.DATA_SOURCE,
                markdownRenderer.plainText(fullDataSource.get("title")),
                "",
                requiredHttpsUrl(
                        fullDataSource,
                        "url"
                )
        );
        state.setDatabaseContainerId(
                dataSourceId,
                NotionObjectType.DATA_SOURCE,
                databaseContainerId(fullDataSource.get("parent"))
        );
        collectDataSourceRows(
                dataSourceId,
                apiClient.queryDataSource(
                        dataSourceId,
                        accessCredential
                ),
                accessCredential,
                state
        );
        state.finishDataSource(dataSourceId);
    }

    private JsonNode fullDataSource(
            JsonNode dataSource,
            String dataSourceId,
            String accessCredential
    ) {
        JsonNode value = hasFullDataSourceFields(dataSource)
                ? dataSource
                : apiClient.retrieveDataSource(
                        dataSourceId,
                        accessCredential
                );
        String objectType = requiredNonBlankString(
                value,
                "object"
        );
        String responseDataSourceId = requiredNotionId(
                value,
                "id"
        );
        if (!"data_source".equals(objectType) || !dataSourceId.equals(responseDataSourceId)) {
            throw invalidResponse();
        }
        return value;
    }

    private boolean hasFullDataSourceFields(JsonNode dataSource) {
        return dataSource.get("title") != null && dataSource.get("parent") != null && dataSource.get("url") != null;
    }

    private void collectDataSourceRows(
            String dataSourceId,
            List<JsonNode> rows,
            String accessCredential,
            NotionCollectionState state
    ) {
        long rowOrder = 0;
        for (JsonNode row : rows) {
            String objectType = requiredNonBlankString(
                    row,
                    "object"
            );
            long order = rowOrder++;
            switch (objectType) {
                case "page" -> collectPage(
                        requiredNotionId(
                                row,
                                "id"
                        ),
                        dataSourceId,
                        order,
                        PlacementPriority.STRUCTURAL,
                        true,
                        accessCredential,
                        state
                );
                case "data_source" -> collectNestedDataSource(
                        row,
                        dataSourceId,
                        order,
                        accessCredential,
                        state
                );
                default -> throw invalidResponse();
            }
        }
    }

    private void collectNestedDataSource(
            JsonNode dataSource,
            String parentDataSourceId,
            long order,
            String accessCredential,
            NotionCollectionState state
    ) {
        String dataSourceId = requiredNotionId(
                dataSource,
                "id"
        );
        state.place(
                dataSourceId,
                NotionObjectType.DATA_SOURCE,
                parentDataSourceId,
                order,
                PlacementPriority.STRUCTURAL
        );
        collectDataSource(
                dataSource,
                accessCredential,
                state
        );
    }

    private void collectPage(
            String pageId,
            String parentId,
            long order,
            NotionCollectionState.PlacementPriority priority,
            boolean dataSourceRow,
            String accessCredential,
            NotionCollectionState state
    ) {
        state.place(
                pageId,
                NotionObjectType.PAGE,
                parentId,
                order,
                priority
        );
        if (dataSourceRow) {
            state.markDataSourceRow(pageId);
        }
        if (!state.startPage(pageId)) {
            return;
        }
        parsePage(
                apiClient.retrievePage(
                        pageId,
                        accessCredential
                ),
                pageId,
                state
        );
        PageBlockContext blockContext = new PageBlockContext(pageId);
        RenderedNotionMarkdown rendered = markdownRenderer.render(
                collectBlocks(
                        pageId,
                        accessCredential,
                        state,
                        blockContext
                )
        );
        state.setMarkdown(
                pageId,
                rendered.markdown()
        );
        state.mergeSkippedBlocks(rendered.skippedBlockCounts());
        state.finishPage(pageId);
        collectChildPages(
                blockContext.childPages,
                accessCredential,
                state
        );
    }

    private void parsePage(
            JsonNode page,
            String pageId,
            NotionCollectionState state
    ) {
        String objectType = requiredNonBlankString(
                page,
                "object"
        );
        String responsePageId = requiredNotionId(
                page,
                "id"
        );
        if (!"page".equals(objectType) || !pageId.equals(responsePageId)) {
            throw invalidResponse();
        }
        PageProperties properties = parsePageProperties(page.get("properties"));
        state.setContent(
                pageId,
                NotionObjectType.PAGE,
                properties.title(),
                "",
                requiredHttpsUrl(
                        page,
                        "url"
                )
        );
        state.setPageProperties(
                pageId,
                properties.complexPropertyCounts()
        );
        applyParentReference(
                pageId,
                parentReference(page.get("parent")),
                state
        );
    }

    private void applyParentReference(
            String pageId,
            ParentReference parent,
            NotionCollectionState state
    ) {
        if (parent.parentId() != null) {
            state.place(
                    pageId,
                    NotionObjectType.PAGE,
                    parent.parentId(),
                    state.nextOrder(),
                    PlacementPriority.RESPONSE_PARENT
            );
        }
        state.setDatabaseContainerId(
                pageId,
                NotionObjectType.PAGE,
                parent.databaseContainerId()
        );
    }

    private PageProperties parsePageProperties(JsonNode properties) {
        requireObject(properties);
        String title = "";
        Map<String, Integer> complexPropertyCounts = new HashMap<>();
        for (Map.Entry<String, JsonNode> entry : properties.properties()) {
            JsonNode property = entry.getValue();
            String type = requiredNonBlankString(
                    property,
                    "type"
            );
            if ("title".equals(type)) {
                title = markdownRenderer.plainText(property.get("title"));
            } else if (COMPLEX_PROPERTY_TYPES.contains(type)) {
                complexPropertyCounts.merge(
                        type,
                        1,
                        Integer::sum
                );
            }
        }
        return new PageProperties(
                title,
                complexPropertyCounts
        );
    }

    private List<NotionBlock> collectBlocks(
            String parentBlockId,
            String accessCredential,
            NotionCollectionState state,
            PageBlockContext context
    ) {
        List<NotionBlock> blocks = new ArrayList<>();
        for (JsonNode blockValue : apiClient.retrieveBlockChildren(
                parentBlockId,
                accessCredential
        )) {
            String blockId = requiredNotionId(
                    blockValue,
                    "id"
            );
            if (!state.markBlockSeen(blockId)) {
                continue;
            }
            String blockType = requiredNonBlankString(
                    blockValue,
                    "type"
            );
            collectStructuralPlacement(
                    blockType,
                    blockId,
                    context,
                    state
            );
            blocks.add(
                    new NotionBlock(
                            blockValue,
                            collectBlockChildren(
                                    blockValue,
                                    blockType,
                                    blockId,
                                    accessCredential,
                                    state,
                                    context
                            )
                    )
            );
        }
        return blocks;
    }

    private List<NotionBlock> collectBlockChildren(
            JsonNode block,
            String blockType,
            String blockId,
            String accessCredential,
            NotionCollectionState state,
            PageBlockContext context
    ) {
        if (!hasChildren(block) || isStructuralBlock(blockType)) {
            return List.of();
        }
        return collectBlocks(
                blockId,
                accessCredential,
                state,
                context
        );
    }

    private void collectStructuralPlacement(
            String blockType,
            String blockId,
            PageBlockContext context,
            NotionCollectionState state
    ) {
        long order = context.nextOrder();
        if ("child_page".equals(blockType)) {
            context.childPages.add(
                    new PagePlacement(
                            blockId,
                            context.pageId,
                            order
                    )
            );
        }
        if ("child_database".equals(blockType)) {
            state.placeDatabase(
                    blockId,
                    context.pageId,
                    order
            );
        }
    }

    private void collectChildPages(
            List<PagePlacement> childPages,
            String accessCredential,
            NotionCollectionState state
    ) {
        for (PagePlacement childPage : childPages) {
            collectPage(
                    childPage.pageId(),
                    childPage.parentId(),
                    childPage.order(),
                    PlacementPriority.STRUCTURAL,
                    false,
                    accessCredential,
                    state
            );
        }
    }

    private boolean hasChildren(JsonNode block) {
        JsonNode hasChildren = block.get("has_children");
        if (hasChildren == null || !hasChildren.isBoolean()) {
            throw invalidResponse();
        }
        return hasChildren.asBoolean();
    }

    private boolean isStructuralBlock(String blockType) {
        return "child_page".equals(blockType) || "child_database".equals(blockType);
    }

    private String databaseContainerId(JsonNode parent) {
        return parentReference(parent).databaseContainerId();
    }

    private ParentReference parentReference(JsonNode parent) {
        requireObject(parent);
        String type = requiredNonBlankString(
                parent,
                "type"
        );
        return switch (type) {
            case "page_id" -> new ParentReference(
                    requiredNotionId(
                            parent,
                            "page_id"
                    ),
                    null
            );
            case "data_source_id" -> new ParentReference(
                    requiredNotionId(
                            parent,
                            "data_source_id"
                    ),
                    null
            );
            case "database_id" -> new ParentReference(
                    null,
                    requiredNotionId(
                            parent,
                            "database_id"
                    )
            );
            case "workspace", "block_id" -> new ParentReference(
                    null,
                    null
            );
            default -> throw invalidResponse();
        };
    }

    private String requiredHttpsUrl(
            JsonNode node,
            String fieldName
    ) {
        String value = requiredNonBlankString(
                node,
                fieldName
        );
        try {
            URI uri = URI.create(value);
            if (!uri.isAbsolute() || !"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                    || uri.getUserInfo() != null) {
                throw invalidResponse();
            }
            return value;
        } catch (IllegalArgumentException exception) {
            throw invalidResponse();
        }
    }

    private void recordSkippedElements(NotionCollectionState state) {
        state.skippedBlockCounts()
                .forEach(observer::recordSkippedBlock);
        state.skippedPropertyCounts()
                .forEach(observer::recordSkippedProperty);
    }

    private void validateAccessCredential(String accessCredential) {
        if (accessCredential == null || accessCredential.isBlank()
                || accessCredential.length() > MAX_ACCESS_CREDENTIAL_LENGTH || accessCredential.indexOf('\r') >= 0
                || accessCredential.indexOf('\n') >= 0) {
            throw new NotionCollectionException(NotionCollectionFailureType.INVALID_REQUEST);
        }
    }

    private static Set<String> complexPropertyTypes() {
        return Set.of(
                "relation",
                "rollup",
                "formula",
                "people",
                "files"
        );
    }

    private record ParentReference(
            String parentId,
            String databaseContainerId
    ) {
    }

    private record PagePlacement(
            String pageId,
            String parentId,
            long order
    ) {
    }

    private record PageProperties(
            String title,
            Map<String, Integer> complexPropertyCounts
    ) {
    }

    private static final class PageBlockContext {
        private final String pageId;
        private final List<PagePlacement> childPages = new ArrayList<>();
        private long nextOrder;

        private PageBlockContext(String pageId) {
            this.pageId = pageId;
        }

        private long nextOrder() {
            return nextOrder++;
        }
    }
}
