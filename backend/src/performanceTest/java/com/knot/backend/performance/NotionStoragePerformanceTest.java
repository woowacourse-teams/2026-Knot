package com.knot.backend.performance;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.model.HostConfig;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.lang.management.MemoryUsage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

@Tag("performance")
class NotionStoragePerformanceTest {
    private static final DockerImageName POSTGRES_IMAGE = DockerImageName.parse("postgres:18.4");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final int RICH_TEXT_SEGMENTS_PER_BLOCK = 3;
    private static final int DEFAULT_PAGE_OBJECT_ID = 1;
    private static final long DATASET_SEED = 20260812L;
    private static final String BASELINE_SQL = "performance/baseline-a.sql";
    private static final String EXPERIMENT_SQL = "performance/experiment-schema.sql";
    private static final String POSITION_KEY_REORDER_SQL =
            "UPDATE experiment_compact_blocks SET position_key = ? WHERE source_block_object_id = "
                    + "(SELECT source_block_object_id FROM experiment_compact_blocks "
                    + "WHERE page_object_id = ? ORDER BY position_key OFFSET ? LIMIT 1)";
    private final Path outputDirectory;

    NotionStoragePerformanceTest() {
        outputDirectory =
                Path.of(
                        System.getProperty(
                                "performance.outputDir", "build/reports/performance/latest"));
    }

    @Test
    void notion_ddl_storage_models_are_measured_under_memory_limits() throws Exception {
        // given
        PerformanceProperties properties = PerformanceProperties.fromSystemProperties();
        ReportWriter reportWriter = new ReportWriter(outputDirectory);

        // when
        PerformanceReport report = runExperimentMatrix(properties);

        // then
        reportWriter.write(report);
        assertEquals(0, report.failedLogicalEquivalenceCount());
        assertEquals(0, report.invalidMatrixCount());
    }

    private PerformanceReport runExperimentMatrix(PerformanceProperties properties)
            throws Exception {
        PerformanceReport report = new PerformanceReport(properties);
        for (int memoryMiB : properties.memoryMiB()) {
            for (int blockCount : properties.sizes()) {
                runDatasetExperiment(properties, report, memoryMiB, blockCount);
            }
        }
        return report;
    }

    private void runDatasetExperiment(
            PerformanceProperties properties,
            PerformanceReport report,
            int memoryMiB,
            int blockCount)
            throws Exception {
        PostgreSQLContainer<?> container = createContainer(memoryMiB);
        try {
            container.start();
            ActiveDatabase.connect(
                    container.getJdbcUrl(), container.getUsername(), container.getPassword());
            try (Connection connection = openConnection(container)) {
                configureSession(connection);
                applySchema(connection);
                report.add(
                        environmentMetric(
                                "RESOURCE_AFTER_START", memoryMiB, blockCount, container));
                DatasetContext datasetContext = loadDataset(connection, blockCount);
                report.add(datasetContext.metric(memoryMiB));
                report.add(
                        resourceMetric(
                                "RESOURCE_AFTER_SEED",
                                memoryMiB,
                                blockCount,
                                container,
                                connection));
                runReadMeasurements(properties, report, connection, memoryMiB, blockCount);
                report.add(
                        resourceMetric(
                                "RESOURCE_AFTER_READS",
                                memoryMiB,
                                blockCount,
                                container,
                                connection));
                runReorderMeasurements(properties, report, connection, memoryMiB, blockCount);
                report.add(
                        resourceMetric(
                                "RESOURCE_AFTER_MATRIX",
                                memoryMiB,
                                blockCount,
                                container,
                                connection));
            }
        } catch (Exception exception) {
            if (isOomKilled(container)) {
                report.add(Metric.resourceLimit(memoryMiB, blockCount, "MATRIX", exception));
            } else {
                report.add(Metric.failure(memoryMiB, blockCount, "MATRIX", exception));
            }
        } finally {
            report.add(
                    containerStateMetric(
                            "RESOURCE_BEFORE_CONTAINER_STOP", memoryMiB, blockCount, container));
            container.stop();
        }
    }

    private PostgreSQLContainer<?> createContainer(int memoryMiB) {
        long memoryBytes = memoryMiB * 1024L * 1024L;
        int sharedBuffersMiB = Math.max(32, memoryMiB / 4);
        return new PostgreSQLContainer<>(POSTGRES_IMAGE)
                .withDatabaseName("knot_performance")
                .withUsername("knot")
                .withPassword("knot")
                .withStartupTimeout(Duration.ofSeconds(60))
                .waitingFor(Wait.forListeningPort().withStartupTimeout(Duration.ofSeconds(60)))
                .withCommand(
                        "postgres",
                        "-c",
                        "shared_buffers=" + sharedBuffersMiB + "MB",
                        "-c",
                        "work_mem=2MB",
                        "-c",
                        "max_connections=64",
                        "-c",
                        "track_io_timing=on")
                .withCreateContainerCmdModifier(
                        command -> {
                            HostConfig hostConfig = command.getHostConfig();
                            if (hostConfig == null) {
                                hostConfig = new HostConfig();
                            }
                            command.withHostConfig(
                                    hostConfig.withMemory(memoryBytes).withMemorySwap(memoryBytes));
                        });
    }

    private Connection openConnection(PostgreSQLContainer<?> container) throws SQLException {
        return DriverManager.getConnection(
                container.getJdbcUrl(), container.getUsername(), container.getPassword());
    }

    private void configureSession(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("SET lock_timeout = '5s'");
            statement.execute("SET statement_timeout = '60s'");
        }
    }

    private void applySchema(Connection connection) throws SQLException {
        executeSqlResource(connection, BASELINE_SQL);
        executeSqlResource(connection, EXPERIMENT_SQL);
    }

    private void executeSqlResource(Connection connection, String resourceName)
            throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(readResource(resourceName));
        }
    }

    private DatasetContext loadDataset(Connection connection, int blockCount) throws Exception {
        Instant startedAt = Instant.now();
        SqlCounter sqlCounter = new SqlCounter();
        String walStart = currentWalLsn(connection);
        connection.setAutoCommit(false);
        try {
            long userId =
                    insertReturningId(
                            connection,
                            "INSERT INTO users (email, name) VALUES (?, ?) RETURNING id",
                            "performance-" + blockCount + "@example.test",
                            "Performance User");
            sqlCounter.increment();
            long workspaceId =
                    insertReturningId(
                            connection,
                            "INSERT INTO workspaces (name, created_by_user_id) VALUES (?, ?) RETURNING id",
                            "Performance Workspace",
                            userId);
            sqlCounter.increment();
            insert(
                    connection,
                    "INSERT INTO workspace_members (workspace_id, user_id, member_role) VALUES (?, ?, ?)",
                    workspaceId,
                    userId,
                    "OWNER");
            sqlCounter.increment();
            long connectionId =
                    insertReturningId(
                            connection,
                            "INSERT INTO notion_connections "
                                    + "(workspace_id, connected_by_user_id, bot_id, notion_workspace_id, "
                                    + "notion_workspace_name, encrypted_access_token) "
                                    + "VALUES (?, ?, ?, ?, ?, ?) RETURNING id",
                            workspaceId,
                            userId,
                            "bot-" + blockCount,
                            "workspace-" + blockCount,
                            "Workspace",
                            "[REDACTED]");
            sqlCounter.increment();
            long notionUserId =
                    insertReturningId(
                            connection,
                            "INSERT INTO notion_users (notion_connection_id, notion_user_id, user_type, name) "
                                    + "VALUES (?, ?, ?, ?) RETURNING id",
                            connectionId,
                            "notion-user-" + blockCount,
                            "PERSON",
                            "Writer");
            sqlCounter.increment();
            long titleRichTextId =
                    insertReturningId(
                            connection,
                            "INSERT INTO rich_text_contents DEFAULT VALUES RETURNING id");
            sqlCounter.increment();
            long pageObjectId =
                    insertObject(
                            connection,
                            workspaceId,
                            connectionId,
                            null,
                            "page-" + blockCount,
                            "PAGE",
                            0,
                            notionUserId);
            sqlCounter.increment();
            insert(
                    connection,
                    "INSERT INTO pages (object_id, title_rich_text_id, plain_title) VALUES (?, ?, ?)",
                    pageObjectId,
                    titleRichTextId,
                    "Performance Page " + blockCount);
            sqlCounter.increment();
            insertPageSnapshot(connection, connectionId, blockCount);
            sqlCounter.increment();
            insertBlockRichTextContents(connection, blockCount);
            sqlCounter.increment();
            insertBlockObjects(
                    connection, blockCount, workspaceId, connectionId, pageObjectId, notionUserId);
            sqlCounter.increment();
            insertBlockRows(connection);
            sqlCounter.increment();
            insertBlockRichTextFields(connection);
            sqlCounter.increment();
            insertRichTextSegments(connection);
            sqlCounter.increment();
            insertRichTextMentions(connection, pageObjectId);
            sqlCounter.increment();
            insertBlockSnapshots(connection, blockCount, connectionId);
            sqlCounter.increment();
            JsonNode documentJson = assembleDocumentBatch(connection, pageObjectId).json();
            insert(
                    connection,
                    "INSERT INTO page_render_snapshots "
                            + "(page_object_id, document_json, source_updated_at) VALUES (?, ?::jsonb, now())",
                    pageObjectId,
                    documentJson.toString());
            sqlCounter.increment();
            insertCompactBlocks(connection, pageObjectId);
            sqlCounter.increment();
            connection.commit();
            try (Statement statement = connection.createStatement()) {
                statement.execute("ANALYZE");
            }
            long elapsedMillis = Duration.between(startedAt, Instant.now()).toMillis();
            long walBytes = walBytesBetween(connection, walStart, currentWalLsn(connection));
            long estimatedPhysicalRows =
                    queryLong(
                            connection,
                            "SELECT COALESCE(SUM(reltuples), 0)::bigint FROM pg_class "
                                    + "WHERE relnamespace = 'public'::regnamespace AND relkind = 'r'");
            long schemaTableCount =
                    queryLong(
                            connection,
                            "SELECT COUNT(*) FROM pg_tables WHERE schemaname = 'public'");
            return new DatasetContext(
                    pageObjectId,
                    connectionId,
                    blockCount,
                    elapsedMillis,
                    sqlCounter.count(),
                    walBytes,
                    estimatedPhysicalRows,
                    schemaTableCount);
        } catch (Exception exception) {
            connection.rollback();
            throw exception;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    private long insertObject(
            Connection connection,
            long workspaceId,
            long connectionId,
            Long parentObjectId,
            String notionObjectId,
            String objectType,
            int positionIndex,
            long notionUserId)
            throws SQLException {
        return insertReturningId(
                connection,
                "INSERT INTO objects "
                        + "(workspace_id, notion_connection_id, parent_object_id, external_parent_type, "
                        + "external_parent_notion_id, parent_payload, notion_object_id, object_type, "
                        + "source_type, position_index, created_by_notion_user_id, updated_by_notion_user_id, "
                        + "notion_created_at, notion_updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?, now(), now()) RETURNING id",
                workspaceId,
                connectionId,
                parentObjectId,
                parentObjectId == null ? "workspace" : "page_id",
                parentObjectId == null ? null : "page-parent",
                "{\"type\":\"synthetic\"}",
                notionObjectId,
                objectType,
                "NOTION",
                positionIndex,
                notionUserId,
                notionUserId);
    }

    private void insertSnapshot(
            Connection connection,
            long connectionId,
            String notionObjectId,
            String objectType,
            String payload)
            throws SQLException {
        insert(
                connection,
                "INSERT INTO notion_object_snapshots "
                        + "(notion_connection_id, notion_object_id, object_type, api_version, raw_payload) "
                        + "VALUES (?, ?, ?, ?, ?::jsonb)",
                connectionId,
                notionObjectId,
                objectType,
                "2025-09-03",
                payload);
    }

    private void insertPageSnapshot(Connection connection, long connectionId, int blockCount)
            throws SQLException {
        insertSnapshot(
                connection, connectionId, "page-" + blockCount, "PAGE", pagePayload(blockCount));
    }

    private void insertBlockRichTextContents(Connection connection, int blockCount)
            throws SQLException {
        execute(
                connection,
                "INSERT INTO rich_text_contents SELECT FROM generate_series(0, ? - 1)",
                blockCount);
    }

    private void insertBlockObjects(
            Connection connection,
            int blockCount,
            long workspaceId,
            long connectionId,
            long pageObjectId,
            long notionUserId)
            throws SQLException {
        execute(
                connection,
                """
                        INSERT INTO objects (
                            workspace_id,
                            notion_connection_id,
                            parent_object_id,
                            external_parent_type,
                            external_parent_notion_id,
                            parent_payload,
                            notion_object_id,
                            object_type,
                            source_type,
                            position_index,
                            created_by_notion_user_id,
                            updated_by_notion_user_id,
                            notion_created_at,
                            notion_updated_at
                        )
                        SELECT
                            ?,
                            ?,
                            ?,
                            'page_id',
                            'page-parent',
                            '{"type":"synthetic"}'::jsonb,
                            'block-' || ? || '-' || index,
                            'BLOCK',
                            'NOTION',
                            index,
                            ?,
                            ?,
                            now(),
                            now()
                        FROM generate_series(0, ? - 1) AS generated(index)
                        ORDER BY index
                        """,
                workspaceId,
                connectionId,
                pageObjectId,
                blockCount,
                notionUserId,
                notionUserId,
                blockCount);
    }

    private void insertBlockRows(Connection connection) throws SQLException {
        execute(
                connection,
                """
                        INSERT INTO blocks (object_id, block_type, has_children, block_payload)
                        SELECT
                            id,
                            CASE
                                WHEN position_index % 17 = 0 THEN 'heading_2'
                                WHEN position_index % 11 = 0 THEN 'to_do'
                                WHEN position_index % 7 = 0 THEN 'image'
                                ELSE 'paragraph'
                            END,
                            position_index % 100 = 0,
                            jsonb_build_object(
                                'type',
                                CASE
                                    WHEN position_index % 17 = 0 THEN 'heading_2'
                                    WHEN position_index % 11 = 0 THEN 'to_do'
                                    WHEN position_index % 7 = 0 THEN 'image'
                                    ELSE 'paragraph'
                                END,
                                'position',
                                position_index,
                                'seed',
                                ?
                            )
                        FROM objects
                        WHERE object_type = 'BLOCK'
                        ORDER BY position_index
                        """,
                DATASET_SEED);
    }

    private void insertBlockRichTextFields(Connection connection) throws SQLException {
        execute(
                connection,
                """
                        INSERT INTO block_rich_text_fields (block_object_id, field_name, rich_text_content_id)
                        SELECT id, 'rich_text', id
                        FROM objects
                        WHERE object_type = 'BLOCK'
                        ORDER BY position_index
                        """);
    }

    private void insertRichTextSegments(Connection connection) throws SQLException {
        execute(
                connection,
                """
                        INSERT INTO rich_text_segments (
                            rich_text_content_id,
                            position_index,
                            segment_type,
                            plain_text,
                            text_content,
                            annotations,
                            segment_payload
                        )
                        SELECT
                            o.id,
                            segment_index,
                            CASE WHEN o.position_index % 10 = 0 AND segment_index = 0 THEN 'mention' ELSE 'text' END,
                            'block-' || o.position_index || '-segment-' || segment_index,
                            'block-' || o.position_index || '-segment-' || segment_index,
                            '{"bold":false,"italic":false}'::jsonb,
                            '{"source":"synthetic"}'::jsonb
                        FROM objects o
                        CROSS JOIN generate_series(0, ? - 1) AS segment(segment_index)
                        WHERE o.object_type = 'BLOCK'
                        ORDER BY o.position_index, segment_index
                        """,
                RICH_TEXT_SEGMENTS_PER_BLOCK);
    }

    private void insertRichTextMentions(Connection connection, long pageObjectId)
            throws SQLException {
        execute(
                connection,
                """
                        INSERT INTO rich_text_mentions (
                            rich_text_segment_id,
                            mention_type,
                            target_page_object_id,
                            mention_payload
                        )
                        SELECT rts.id, 'PAGE', ?, '{"synthetic":true}'::jsonb
                        FROM rich_text_segments rts
                        JOIN objects o ON o.id = rts.rich_text_content_id
                        WHERE o.position_index % 10 = 0 AND rts.position_index = 0
                        """,
                pageObjectId);
    }

    private void insertBlockSnapshots(Connection connection, int blockCount, long connectionId)
            throws SQLException {
        execute(
                connection,
                """
                        INSERT INTO notion_object_snapshots (
                            notion_connection_id,
                            notion_object_id,
                            object_type,
                            api_version,
                            raw_payload
                        )
                        SELECT
                            ?,
                            'block-' || ? || '-' || position_index,
                            'BLOCK',
                            '2025-09-03',
                            block_payload
                        FROM objects o
                        JOIN blocks b ON b.object_id = o.id
                        WHERE o.object_type = 'BLOCK'
                        ORDER BY o.position_index
                        """,
                connectionId,
                blockCount);
    }

    private void insertCompactBlocks(Connection connection, long pageObjectId) throws SQLException {
        execute(
                connection,
                """
                INSERT INTO experiment_compact_blocks (
                    page_object_id,
                    source_block_object_id,
                    parent_block_object_id,
                    position_key,
                    block_type,
                    payload
                )
                WITH rich_text_by_block AS (
                    SELECT
                        brtf.block_object_id,
                        jsonb_agg(
                            jsonb_strip_nulls(
                                jsonb_build_object(
                                    'position', rts.position_index,
                                    'plainText', rts.plain_text,
                                    'mentionType', rtm.mention_type
                                )
                            )
                            ORDER BY rts.position_index
                        ) AS rich_text
                    FROM block_rich_text_fields brtf
                    JOIN rich_text_segments rts
                        ON rts.rich_text_content_id = brtf.rich_text_content_id
                    LEFT JOIN rich_text_mentions rtm
                        ON rtm.rich_text_segment_id = rts.id
                    GROUP BY brtf.block_object_id
                )
                SELECT
                    ?,
                    o.id,
                    COALESCE(o.parent_object_id, ?),
                    lpad((o.position_index * 1024)::text, 12, '0'),
                    b.block_type,
                    jsonb_build_object(
                        'position',
                        o.position_index,
                        'richText',
                        COALESCE(rich_text_by_block.rich_text, '[]'::jsonb)
                    )
                FROM objects o
                JOIN blocks b ON b.object_id = o.id
                LEFT JOIN rich_text_by_block ON rich_text_by_block.block_object_id = o.id
                WHERE o.object_type = 'BLOCK'
                ORDER BY o.position_index
                """,
                pageObjectId,
                pageObjectId);
    }

    private void runReadMeasurements(
            PerformanceProperties properties,
            PerformanceReport report,
            Connection connection,
            int memoryMiB,
            int blockCount)
            throws Exception {
        DocumentResult reference = assembleDocumentBatch(connection, DEFAULT_PAGE_OBJECT_ID);
        String referenceHash = sha256(canonicalJson(reference.json()));
        report.add(measureSnapshotGenerate(connection, memoryMiB, blockCount));
        for (ReadModel readModel : ReadModel.values()) {
            captureExplain(connection, report, memoryMiB, blockCount, readModel);
            List<Measurement> measurements = new ArrayList<>();
            for (int iteration = -properties.warmups();
                    iteration < properties.iterations();
                    iteration++) {
                Measurement measurement =
                        measureRead(connection, readModel, DEFAULT_PAGE_OBJECT_ID);
                if (iteration >= 0) {
                    measurements.add(measurement);
                    if (!referenceHash.equals(measurement.responseHash())) {
                        report.add(
                                Metric.logicalMismatch(
                                        memoryMiB,
                                        blockCount,
                                        readModel.name(),
                                        referenceHash,
                                        measurement));
                    }
                }
            }
            report.add(
                    Metric.fromMeasurements(memoryMiB, blockCount, readModel.name(), measurements));
        }
    }

    private Measurement measureRead(Connection connection, ReadModel readModel, long pageObjectId)
            throws Exception {
        MemorySample.resetPeaks();
        MemorySample before = MemorySample.capture();
        SqlCounter sqlCounter = new SqlCounter();
        Instant startedAt = Instant.now();
        DocumentResult result =
                switch (readModel) {
                    case N_PLUS_ONE ->
                            assembleDocumentNPlusOne(connection, pageObjectId, sqlCounter);
                    case BATCH -> assembleDocumentBatch(connection, pageObjectId, sqlCounter);
                    case SNAPSHOT_READ -> readSnapshot(connection, pageObjectId, sqlCounter);
                    case COMPACT -> readCompact(connection, pageObjectId, sqlCounter);
                };
        long elapsedNanos = Duration.between(startedAt, Instant.now()).toNanos();
        MemorySample after = MemorySample.capture();
        return new Measurement(
                elapsedNanos,
                sqlCounter.count(),
                result.json().toString().getBytes(StandardCharsets.UTF_8).length,
                sha256(canonicalJson(result.json())),
                before.heapUsedBytes(),
                after.heapUsedBytes(),
                after.heapPeakBytes());
    }

    private DocumentResult assembleDocumentBatch(Connection connection, long pageObjectId)
            throws Exception {
        return assembleDocumentBatch(connection, pageObjectId, new SqlCounter());
    }

    private DocumentResult assembleDocumentBatch(
            Connection connection, long pageObjectId, SqlCounter sqlCounter) throws Exception {
        ObjectNode document = documentRoot(pageObjectId);
        ArrayNode blocks = document.putArray("blocks");
        String sql =
                """
                SELECT o.id, o.position_index, b.block_type,
                    rts.position_index AS segment_index, rts.plain_text, rtm.mention_type
                FROM objects o
                JOIN blocks b ON b.object_id = o.id
                JOIN block_rich_text_fields brtf ON brtf.block_object_id = b.object_id
                JOIN rich_text_segments rts ON rts.rich_text_content_id = brtf.rich_text_content_id
                LEFT JOIN rich_text_mentions rtm ON rtm.rich_text_segment_id = rts.id
                WHERE o.parent_object_id = ?
                ORDER BY o.position_index, rts.position_index
                """;
        Map<Long, ObjectNode> blockById = new LinkedHashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, pageObjectId);
            sqlCounter.increment();
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    long blockObjectId = resultSet.getLong("id");
                    ObjectNode block =
                            blockById.computeIfAbsent(
                                    blockObjectId,
                                    id -> {
                                        ObjectNode node = blocks.addObject();
                                        node.put("id", id);
                                        node.put("type", uncheckedString(resultSet, "block_type"));
                                        node.put(
                                                "position",
                                                uncheckedInt(resultSet, "position_index"));
                                        node.putArray("richText");
                                        return node;
                                    });
                    ObjectNode segment = ((ArrayNode) block.get("richText")).addObject();
                    segment.put("position", resultSet.getInt("segment_index"));
                    segment.put("plainText", resultSet.getString("plain_text"));
                    putMentionType(segment, resultSet.getString("mention_type"));
                }
            }
        }
        sortBlocks(blocks);
        return new DocumentResult(document);
    }

    private Metric measureSnapshotGenerate(Connection connection, int memoryMiB, int blockCount)
            throws Exception {
        String walStart = currentWalLsn(connection);
        MemorySample.resetPeaks();
        MemorySample before = MemorySample.capture();
        Instant startedAt = Instant.now();
        SqlCounter sqlCounter = new SqlCounter();
        DocumentResult documentResult =
                assembleDocumentBatch(connection, DEFAULT_PAGE_OBJECT_ID, sqlCounter);
        int rows;
        try (PreparedStatement statement =
                connection.prepareStatement(
                        "UPDATE page_render_snapshots SET version = version + 1, "
                                + "document_json = ?::jsonb, generated_at = now() "
                                + "WHERE page_object_id = ?")) {
            statement.setString(1, documentResult.json().toString());
            statement.setLong(2, DEFAULT_PAGE_OBJECT_ID);
            rows = statement.executeUpdate();
            sqlCounter.increment();
        }
        long elapsedNanos = Duration.between(startedAt, Instant.now()).toNanos();
        MemorySample after = MemorySample.capture();
        long walBytes = walBytesBetween(connection, walStart, currentWalLsn(connection));
        return Metric.write(
                memoryMiB,
                blockCount,
                "SNAPSHOT_GENERATE",
                elapsedNanos,
                sqlCounter.count(),
                documentResult.json().toString().getBytes(StandardCharsets.UTF_8).length,
                after.heapUsedBytes() - before.heapUsedBytes(),
                after.heapPeakBytes(),
                rows,
                walBytes,
                "batch assembly + snapshot upsert");
    }

    private DocumentResult assembleDocumentNPlusOne(
            Connection connection, long pageObjectId, SqlCounter sqlCounter) throws Exception {
        ObjectNode document = documentRoot(pageObjectId);
        ArrayNode blocks = document.putArray("blocks");
        List<BlockRow> blockRows = new ArrayList<>();
        try (PreparedStatement statement =
                connection.prepareStatement(
                        "SELECT o.id, o.position_index, b.block_type FROM objects o "
                                + "JOIN blocks b ON b.object_id = o.id "
                                + "WHERE o.parent_object_id = ? ORDER BY o.position_index")) {
            statement.setLong(1, pageObjectId);
            sqlCounter.increment();
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    blockRows.add(
                            new BlockRow(
                                    resultSet.getLong("id"),
                                    resultSet.getInt("position_index"),
                                    resultSet.getString("block_type")));
                }
            }
        }
        for (BlockRow blockRow : blockRows) {
            ObjectNode block = blocks.addObject();
            block.put("id", blockRow.id());
            block.put("type", blockRow.blockType());
            block.put("position", blockRow.positionIndex());
            ArrayNode richText = block.putArray("richText");
            try (PreparedStatement statement =
                    connection.prepareStatement(
                            "SELECT rts.position_index, rts.plain_text, rtm.mention_type "
                                    + "FROM block_rich_text_fields brtf "
                                    + "JOIN rich_text_segments rts "
                                    + "ON rts.rich_text_content_id = brtf.rich_text_content_id "
                                    + "LEFT JOIN rich_text_mentions rtm "
                                    + "ON rtm.rich_text_segment_id = rts.id "
                                    + "WHERE brtf.block_object_id = ? ORDER BY rts.position_index")) {
                statement.setLong(1, blockRow.id());
                sqlCounter.increment();
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        ObjectNode segment = richText.addObject();
                        segment.put("position", resultSet.getInt("position_index"));
                        segment.put("plainText", resultSet.getString("plain_text"));
                        putMentionType(segment, resultSet.getString("mention_type"));
                    }
                }
            }
        }
        return new DocumentResult(document);
    }

    private DocumentResult readSnapshot(
            Connection connection, long pageObjectId, SqlCounter sqlCounter) throws Exception {
        try (PreparedStatement statement =
                connection.prepareStatement(
                        "SELECT document_json::text FROM page_render_snapshots WHERE page_object_id = ?")) {
            statement.setLong(1, pageObjectId);
            sqlCounter.increment();
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new IllegalStateException("snapshot not found");
                }
                return new DocumentResult(OBJECT_MAPPER.readTree(resultSet.getString(1)));
            }
        }
    }

    private DocumentResult readCompact(
            Connection connection, long pageObjectId, SqlCounter sqlCounter) throws Exception {
        ObjectNode document = documentRoot(pageObjectId);
        ArrayNode blocks = document.putArray("blocks");
        try (PreparedStatement statement =
                connection.prepareStatement(
                        "SELECT source_block_object_id, block_type, payload::text FROM experiment_compact_blocks "
                                + "WHERE page_object_id = ? AND parent_block_object_id = ? ORDER BY position_key")) {
            statement.setLong(1, pageObjectId);
            statement.setLong(2, pageObjectId);
            sqlCounter.increment();
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    JsonNode payload = OBJECT_MAPPER.readTree(resultSet.getString("payload"));
                    ObjectNode block = blocks.addObject();
                    block.put("id", resultSet.getLong("source_block_object_id"));
                    block.put("type", resultSet.getString("block_type"));
                    block.put("position", payload.get("position").asInt());
                    block.set("richText", payload.get("richText"));
                }
            }
        }
        sortBlocks(blocks);
        return new DocumentResult(document);
    }

    private void captureExplain(
            Connection connection,
            PerformanceReport report,
            int memoryMiB,
            int blockCount,
            ReadModel readModel)
            throws Exception {
        if (readModel == ReadModel.N_PLUS_ONE) {
            String parentPlan =
                    explain(
                            connection,
                            "SELECT o.id, o.position_index, b.block_type FROM objects o "
                                    + "JOIN blocks b ON b.object_id = o.id "
                                    + "WHERE o.parent_object_id = "
                                    + DEFAULT_PAGE_OBJECT_ID
                                    + " ORDER BY o.position_index");
            String childPlan =
                    explain(
                            connection,
                            "SELECT rts.position_index, rts.plain_text, rtm.mention_type "
                                    + "FROM block_rich_text_fields brtf "
                                    + "JOIN rich_text_segments rts "
                                    + "ON rts.rich_text_content_id = brtf.rich_text_content_id "
                                    + "LEFT JOIN rich_text_mentions rtm "
                                    + "ON rtm.rich_text_segment_id = rts.id "
                                    + "WHERE brtf.block_object_id = "
                                    + firstBlockObjectId(connection)
                                    + " ORDER BY rts.position_index");
            report.addPlan(memoryMiB, blockCount, "N_PLUS_ONE_PARENT", parentPlan);
            report.addPlan(memoryMiB, blockCount, "N_PLUS_ONE_CHILD_REPRESENTATIVE", childPlan);
            return;
        }
        String sql =
                switch (readModel) {
                    case BATCH ->
                            """
                    SELECT o.id, o.position_index, b.block_type,
                        rts.position_index AS segment_index, rts.plain_text, rtm.mention_type
                    FROM objects o
                    JOIN blocks b ON b.object_id = o.id
                    JOIN block_rich_text_fields brtf ON brtf.block_object_id = b.object_id
                    JOIN rich_text_segments rts ON rts.rich_text_content_id = brtf.rich_text_content_id
                    LEFT JOIN rich_text_mentions rtm ON rtm.rich_text_segment_id = rts.id
                    WHERE o.parent_object_id = %d
                    ORDER BY o.position_index, rts.position_index
                    """
                                    .formatted(DEFAULT_PAGE_OBJECT_ID);
                    case SNAPSHOT_READ ->
                            "SELECT document_json::text FROM page_render_snapshots WHERE page_object_id = "
                                    + DEFAULT_PAGE_OBJECT_ID;
                    case COMPACT ->
                            "SELECT source_block_object_id, block_type, payload::text FROM experiment_compact_blocks "
                                    + "WHERE page_object_id = "
                                    + DEFAULT_PAGE_OBJECT_ID
                                    + " AND parent_block_object_id = "
                                    + DEFAULT_PAGE_OBJECT_ID
                                    + " ORDER BY position_key";
                    case N_PLUS_ONE -> throw new IllegalStateException("handled above");
                };
        String plan = explain(connection, sql);
        report.addPlan(memoryMiB, blockCount, readModel.name(), plan);
    }

    private long firstBlockObjectId(Connection connection) throws SQLException {
        return queryLong(
                connection,
                "SELECT id FROM objects WHERE object_type = 'BLOCK' ORDER BY position_index LIMIT 1");
    }

    private String explain(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet resultSet =
                        statement.executeQuery(
                                "EXPLAIN (ANALYZE, BUFFERS, WAL, FORMAT JSON) " + sql)) {
            resultSet.next();
            return resultSet.getString(1);
        }
    }

    private void runReorderMeasurements(
            PerformanceProperties properties,
            PerformanceReport report,
            Connection connection,
            int memoryMiB,
            int blockCount)
            throws Exception {
        resetIntegerOrder(connection);
        report.add(measureIntegerReorder(connection, memoryMiB, blockCount));
        resetPositionKeys(connection);
        report.add(measurePositionKeyReorder(connection, memoryMiB, blockCount));
        for (int concurrency : properties.concurrency()) {
            resetIntegerOrder(connection);
            report.add(
                    measureConcurrentReorder(
                            connection,
                            memoryMiB,
                            blockCount,
                            concurrency,
                            "INTEGER_REORDER",
                            this::integerReorderAttempt));
            resetPositionKeys(connection);
            report.add(
                    measureConcurrentReorder(
                            connection,
                            memoryMiB,
                            blockCount,
                            concurrency,
                            "POSITION_KEY_REORDER",
                            this::positionKeyReorderAttempt));
        }
    }

    private Metric measureIntegerReorder(Connection connection, int memoryMiB, int blockCount)
            throws SQLException {
        String walStart = currentWalLsn(connection);
        Instant startedAt = Instant.now();
        int rows;
        try (PreparedStatement statement =
                connection.prepareStatement(
                        "UPDATE objects SET position_index = position_index + 1 WHERE parent_object_id = ? "
                                + "AND position_index >= ?")) {
            statement.setLong(1, DEFAULT_PAGE_OBJECT_ID);
            statement.setInt(2, Math.max(0, blockCount / 2));
            rows = statement.executeUpdate();
        }
        long elapsedNanos = Duration.between(startedAt, Instant.now()).toNanos();
        long walBytes = walBytesBetween(connection, walStart, currentWalLsn(connection));
        return Metric.write(
                memoryMiB,
                blockCount,
                "INTEGER_REORDER",
                elapsedNanos,
                1,
                0,
                0,
                0,
                rows,
                walBytes,
                "range update from midpoint");
    }

    private Metric measurePositionKeyReorder(Connection connection, int memoryMiB, int blockCount)
            throws SQLException {
        String walStart = currentWalLsn(connection);
        Instant startedAt = Instant.now();
        int rows;
        try (PreparedStatement statement = connection.prepareStatement(POSITION_KEY_REORDER_SQL)) {
            statement.setString(1, midpointPositionKey(0, 1024));
            statement.setLong(2, DEFAULT_PAGE_OBJECT_ID);
            statement.setInt(3, Math.max(0, blockCount - 1));
            rows = statement.executeUpdate();
        }
        long elapsedNanos = Duration.between(startedAt, Instant.now()).toNanos();
        long walBytes = walBytesBetween(connection, walStart, currentWalLsn(connection));
        return Metric.write(
                memoryMiB,
                blockCount,
                "POSITION_KEY_REORDER",
                elapsedNanos,
                1,
                0,
                0,
                0,
                rows,
                walBytes,
                "one row moved between adjacent rank keys");
    }

    private Metric measureConcurrentReorder(
            Connection connection,
            int memoryMiB,
            int blockCount,
            int concurrency,
            String scenario,
            ReorderAttempt reorderAttempt)
            throws Exception {
        String walStart = currentWalLsn(connection);
        ExecutorService executorService = Executors.newFixedThreadPool(concurrency);
        CountDownLatch startSignal = new CountDownLatch(1);
        List<Future<AttemptResult>> futures = new ArrayList<>();
        Instant startedAt = Instant.now();
        try {
            for (int index = 0; index < concurrency; index++) {
                int attemptIndex = index;
                futures.add(
                        executorService.submit(
                                () ->
                                        reorderAttempt.attempt(
                                                startSignal, attemptIndex, blockCount)));
            }
            startSignal.countDown();
            List<AttemptResult> results = new ArrayList<>();
            for (Future<AttemptResult> future : futures) {
                results.add(future.get());
            }
            long elapsedNanos = Duration.between(startedAt, Instant.now()).toNanos();
            long walBytes = walBytesBetween(connection, walStart, currentWalLsn(connection));
            return Metric.concurrent(
                    memoryMiB,
                    blockCount,
                    scenario + "_CONCURRENT_" + concurrency,
                    elapsedNanos,
                    walBytes,
                    results);
        } finally {
            executorService.shutdownNow();
        }
    }

    private AttemptResult integerReorderAttempt(
            CountDownLatch startSignal, int attemptIndex, int blockCount) {
        return runAttempt(
                startSignal,
                "INTEGER_REORDER",
                connection -> {
                    try (PreparedStatement statement =
                            connection.prepareStatement(
                                    "UPDATE objects SET position_index = position_index + 1 WHERE parent_object_id = ? "
                                            + "AND position_index >= ?")) {
                        statement.setLong(1, DEFAULT_PAGE_OBJECT_ID);
                        statement.setInt(
                                2,
                                Math.max(0, (attemptIndex + blockCount) % Math.max(1, blockCount)));
                        return statement.executeUpdate();
                    }
                });
    }

    private AttemptResult positionKeyReorderAttempt(
            CountDownLatch startSignal, int attemptIndex, int blockCount) {
        return runAttempt(
                startSignal,
                "POSITION_KEY_REORDER",
                connection -> {
                    try (PreparedStatement statement =
                            connection.prepareStatement(POSITION_KEY_REORDER_SQL)) {
                        statement.setString(1, positionKey(attemptIndex + 1));
                        statement.setLong(2, DEFAULT_PAGE_OBJECT_ID);
                        statement.setInt(3, Math.max(0, blockCount - 1 - attemptIndex));
                        return statement.executeUpdate();
                    }
                });
    }

    private void resetIntegerOrder(Connection connection) throws SQLException {
        execute(
                connection,
                "UPDATE objects SET position_index = split_part(notion_object_id, '-', 3)::integer "
                        + "WHERE object_type = 'BLOCK'");
    }

    private void resetPositionKeys(Connection connection) throws SQLException {
        execute(
                connection,
                "UPDATE experiment_compact_blocks compact SET position_key = "
                        + "lpad((split_part(source.notion_object_id, '-', 3)::integer * 1024)::text, 12, '0') "
                        + "FROM objects source WHERE source.id = compact.source_block_object_id");
    }

    private AttemptResult runAttempt(
            CountDownLatch startSignal, String name, SqlAttempt sqlAttempt) {
        Instant startedAt = Instant.now();
        try (Connection connection =
                DriverManager.getConnection(
                        ActiveDatabase.jdbcUrl(),
                        ActiveDatabase.username(),
                        ActiveDatabase.password())) {
            configureSession(connection);
            startSignal.await();
            int rows = sqlAttempt.execute(connection);
            return AttemptResult.success(
                    name, Duration.between(startedAt, Instant.now()).toNanos(), rows);
        } catch (Exception exception) {
            return AttemptResult.failure(
                    name, Duration.between(startedAt, Instant.now()).toNanos(), exception);
        }
    }

    private Metric environmentMetric(
            String scenario, int memoryMiB, int blockCount, PostgreSQLContainer<?> container)
            throws Exception {
        InspectContainerResponse response =
                container.getDockerClient().inspectContainerCmd(container.getContainerId()).exec();
        Map<String, String> details = new LinkedHashMap<>();
        details.put("containerId", container.getContainerId());
        details.put("image", POSTGRES_IMAGE.asCanonicalNameString());
        details.put("memoryMaxBytes", cgroupValue(container, "/sys/fs/cgroup/memory.max"));
        details.put("memorySwapMaxBytes", cgroupValue(container, "/sys/fs/cgroup/memory.swap.max"));
        details.put("memoryCurrentBytes", cgroupValue(container, "/sys/fs/cgroup/memory.current"));
        details.put("memoryPeakBytes", cgroupValue(container, "/sys/fs/cgroup/memory.peak"));
        details.put(
                "memoryEvents",
                cgroupValue(container, "/sys/fs/cgroup/memory.events").replace('\n', '|'));
        details.put("sharedBuffersMiB", String.valueOf(Math.max(32, memoryMiB / 4)));
        details.put("workMem", "2MB");
        details.put("maxConnections", "64");
        details.put("oomKilled", String.valueOf(response.getState().getOOMKilled()));
        return Metric.observation(memoryMiB, blockCount, scenario, details);
    }

    private Metric resourceMetric(
            String scenario,
            int memoryMiB,
            int blockCount,
            PostgreSQLContainer<?> container,
            Connection connection)
            throws Exception {
        Map<String, String> details = new LinkedHashMap<>();
        details.put(
                "dbSizeBytes",
                String.valueOf(
                        queryLong(connection, "SELECT pg_database_size(current_database())")));
        details.put("memoryCurrentBytes", cgroupValue(container, "/sys/fs/cgroup/memory.current"));
        details.put("memoryPeakBytes", cgroupValue(container, "/sys/fs/cgroup/memory.peak"));
        details.put("memoryMaxBytes", cgroupValue(container, "/sys/fs/cgroup/memory.max"));
        details.put("memorySwapMaxBytes", cgroupValue(container, "/sys/fs/cgroup/memory.swap.max"));
        details.put(
                "memoryEvents",
                cgroupValue(container, "/sys/fs/cgroup/memory.events").replace('\n', '|'));
        InspectContainerResponse response =
                container.getDockerClient().inspectContainerCmd(container.getContainerId()).exec();
        details.put("oomKilled", String.valueOf(response.getState().getOOMKilled()));
        return Metric.observation(memoryMiB, blockCount, scenario, details);
    }

    private Metric containerStateMetric(
            String scenario, int memoryMiB, int blockCount, PostgreSQLContainer<?> container) {
        if (!container.isCreated()) {
            return Metric.observation(
                    memoryMiB, blockCount, scenario, Map.of("state", "not_created"));
        }
        InspectContainerResponse response =
                container.getDockerClient().inspectContainerCmd(container.getContainerId()).exec();
        Map<String, String> details = new LinkedHashMap<>();
        details.put("running", String.valueOf(response.getState().getRunning()));
        details.put("oomKilled", String.valueOf(response.getState().getOOMKilled()));
        details.put("memoryMaxBytes", safeCgroupValue(container, "/sys/fs/cgroup/memory.max"));
        details.put(
                "memorySwapMaxBytes", safeCgroupValue(container, "/sys/fs/cgroup/memory.swap.max"));
        details.put(
                "memoryCurrentBytes", safeCgroupValue(container, "/sys/fs/cgroup/memory.current"));
        details.put("memoryPeakBytes", safeCgroupValue(container, "/sys/fs/cgroup/memory.peak"));
        details.put(
                "memoryEvents",
                safeCgroupValue(container, "/sys/fs/cgroup/memory.events").replace('\n', '|'));
        return Metric.observation(memoryMiB, blockCount, scenario, details);
    }

    private boolean isOomKilled(PostgreSQLContainer<?> container) {
        if (!container.isCreated()) {
            return false;
        }
        InspectContainerResponse response =
                container.getDockerClient().inspectContainerCmd(container.getContainerId()).exec();
        return Boolean.TRUE.equals(response.getState().getOOMKilled());
    }

    private String cgroupValue(PostgreSQLContainer<?> container, String path)
            throws IOException, InterruptedException {
        org.testcontainers.containers.Container.ExecResult result =
                container.execInContainer(
                        "sh", "-c", "test -f " + path + " && cat " + path + " || true");
        return result.getStdout().strip();
    }

    private String safeCgroupValue(PostgreSQLContainer<?> container, String path) {
        try {
            return cgroupValue(container, path);
        } catch (Exception exception) {
            return "unavailable:" + exception.getClass().getSimpleName();
        }
    }

    private String currentWalLsn(Connection connection) throws SQLException {
        return queryString(connection, "SELECT pg_current_wal_lsn()::text");
    }

    private long walBytesBetween(Connection connection, String startLsn, String endLsn)
            throws SQLException {
        try (PreparedStatement statement =
                connection.prepareStatement(
                        "SELECT pg_wal_lsn_diff(?::pg_lsn, ?::pg_lsn)::bigint")) {
            statement.setString(1, endLsn);
            statement.setString(2, startLsn);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getLong(1);
            }
        }
    }

    private String queryString(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(sql)) {
            resultSet.next();
            return resultSet.getString(1);
        }
    }

    private long queryLong(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(sql)) {
            resultSet.next();
            return resultSet.getLong(1);
        }
    }

    private long insertReturningId(Connection connection, String sql, Object... parameters)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, parameters);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getLong(1);
            }
        }
    }

    private void insert(Connection connection, String sql, Object... parameters)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, parameters);
            statement.executeUpdate();
        }
    }

    private void execute(Connection connection, String sql, Object... parameters)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, parameters);
            statement.executeUpdate();
        }
    }

    private void bind(PreparedStatement statement, Object... parameters) throws SQLException {
        for (int index = 0; index < parameters.length; index++) {
            statement.setObject(index + 1, parameters[index]);
        }
    }

    private ObjectNode documentRoot(long pageObjectId) {
        ObjectNode document = OBJECT_MAPPER.createObjectNode();
        document.put("pageId", pageObjectId);
        document.put("title", "Performance Page");
        return document;
    }

    private void sortBlocks(ArrayNode blocks) {
        List<JsonNode> sorted = new ArrayList<>();
        blocks.forEach(sorted::add);
        sorted.sort(Comparator.comparingInt(node -> node.get("position").asInt()));
        blocks.removeAll();
        sorted.forEach(blocks::add);
    }

    private void putMentionType(ObjectNode segment, String mentionType) {
        if (mentionType != null) {
            segment.put("mentionType", mentionType);
        }
    }

    private String readResource(String resourceName) {
        try (InputStream inputStream =
                getClass().getClassLoader().getResourceAsStream(resourceName)) {
            if (inputStream == null) {
                throw new IllegalStateException("classpath resource not found: " + resourceName);
            }
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte item : hash) {
                builder.append(String.format("%02x", item));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private String canonicalJson(JsonNode jsonNode) {
        if (jsonNode.isObject()) {
            List<String> propertyNames = new ArrayList<>(jsonNode.propertyNames());
            Collections.sort(propertyNames);
            StringJoiner joiner = new StringJoiner(",", "{", "}");
            for (String propertyName : propertyNames) {
                joiner.add(
                        quoteJson(propertyName) + ":" + canonicalJson(jsonNode.get(propertyName)));
            }
            return joiner.toString();
        }
        if (jsonNode.isArray()) {
            StringJoiner joiner = new StringJoiner(",", "[", "]");
            for (JsonNode item : jsonNode) {
                joiner.add(canonicalJson(item));
            }
            return joiner.toString();
        }
        return jsonNode.toString();
    }

    private String quoteJson(String value) {
        return OBJECT_MAPPER.writeValueAsString(value);
    }

    private String pagePayload(int blockCount) {
        return "{\"type\":\"page\",\"blockCount\":"
                + blockCount
                + ",\"seed\":"
                + DATASET_SEED
                + "}";
    }

    private String blockPayload(int index) {
        return "{\"type\":\""
                + blockType(index)
                + "\",\"position\":"
                + index
                + ",\"seed\":"
                + DATASET_SEED
                + "}";
    }

    private String compactPayload(int index) {
        StringJoiner segments = new StringJoiner(",", "[", "]");
        for (int segmentIndex = 0; segmentIndex < RICH_TEXT_SEGMENTS_PER_BLOCK; segmentIndex++) {
            segments.add(
                    "{\"position\":"
                            + segmentIndex
                            + ",\"plainText\":\""
                            + segmentText(index, segmentIndex)
                            + "\"}");
        }
        return "{\"position\":" + index + ",\"richText\":" + segments + "}";
    }

    private String blockType(int index) {
        if (index % 17 == 0) {
            return "heading_2";
        }
        if (index % 11 == 0) {
            return "to_do";
        }
        if (index % 7 == 0) {
            return "image";
        }
        return "paragraph";
    }

    private String segmentType(int blockIndex, int segmentIndex) {
        if (blockIndex % 10 == 0 && segmentIndex == 0) {
            return "mention";
        }
        return "text";
    }

    private String segmentText(int blockIndex, int segmentIndex) {
        return "block-" + blockIndex + "-segment-" + segmentIndex;
    }

    private String uncheckedString(ResultSet resultSet, String columnName) {
        try {
            return resultSet.getString(columnName);
        } catch (SQLException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private int uncheckedInt(ResultSet resultSet, String columnName) {
        try {
            return resultSet.getInt(columnName);
        } catch (SQLException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private String positionKey(int index) {
        return String.format("%012d", index);
    }

    private String midpointPositionKey(int lower, int upper) {
        return positionKey(lower + ((upper - lower) / 2));
    }

    private enum ReadModel {
        N_PLUS_ONE,
        BATCH,
        SNAPSHOT_READ,
        COMPACT
    }

    private interface ReorderAttempt {

        AttemptResult attempt(CountDownLatch startSignal, int attemptIndex, int blockCount);
    }

    private interface SqlAttempt {

        int execute(Connection connection) throws Exception;
    }

    private record PerformanceProperties(
            List<Integer> sizes,
            List<Integer> memoryMiB,
            int iterations,
            int warmups,
            List<Integer> concurrency) {

        static PerformanceProperties fromSystemProperties() {
            return new PerformanceProperties(
                    parseInts("performance.sizes", "1000,10000,100000"),
                    parseInts("performance.memoryMiB", "1024,512,256"),
                    Integer.getInteger("performance.iterations", 3),
                    Integer.getInteger("performance.warmups", 1),
                    parseInts("performance.concurrency", "10,50"));
        }

        private static List<Integer> parseInts(String propertyName, String defaultValue) {
            String rawValue = System.getProperty(propertyName, defaultValue);
            List<Integer> values = new ArrayList<>();
            for (String part : rawValue.split(",")) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) {
                    values.add(Integer.parseInt(trimmed));
                }
            }
            return Collections.unmodifiableList(values);
        }
    }

    private record DatasetContext(
            long pageObjectId,
            long connectionId,
            int blockCount,
            long loadMillis,
            long sqlCount,
            long walBytes,
            long estimatedPhysicalRows,
            long schemaTableCount) {

        Metric metric(int memoryMiB) {
            return Metric.observation(
                    memoryMiB,
                    blockCount,
                    "SEED_LOAD",
                    Map.of(
                            "pageObjectId", String.valueOf(pageObjectId),
                            "notionConnectionId", String.valueOf(connectionId),
                            "loadMillis", String.valueOf(loadMillis),
                            "sqlCount", String.valueOf(sqlCount),
                            "logicalBlocks", String.valueOf(blockCount),
                            "walBytes", String.valueOf(walBytes),
                            "estimatedPhysicalRows", String.valueOf(estimatedPhysicalRows),
                            "schemaTableCount", String.valueOf(schemaTableCount)));
        }
    }

    private record BlockRow(long id, int positionIndex, String blockType) {}

    private record DocumentResult(JsonNode json) {}

    private record Measurement(
            long elapsedNanos,
            long sqlCount,
            int responseBytes,
            String responseHash,
            long heapBeforeBytes,
            long heapAfterBytes,
            long heapPeakBytes) {}

    private record MemorySample(long heapUsedBytes, long heapPeakBytes) {

        static void resetPeaks() {
            for (MemoryPoolMXBean memoryPoolMXBean : ManagementFactory.getMemoryPoolMXBeans()) {
                if (memoryPoolMXBean.getType() == MemoryType.HEAP) {
                    memoryPoolMXBean.resetPeakUsage();
                }
            }
        }

        static MemorySample capture() {
            MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
            MemoryUsage heapMemoryUsage = memoryMXBean.getHeapMemoryUsage();
            long heapPeakBytes = 0;
            for (MemoryPoolMXBean memoryPoolMXBean : ManagementFactory.getMemoryPoolMXBeans()) {
                if (memoryPoolMXBean.getType() == MemoryType.HEAP) {
                    MemoryUsage peakUsage = memoryPoolMXBean.getPeakUsage();
                    if (peakUsage != null) {
                        heapPeakBytes += peakUsage.getUsed();
                    }
                }
            }
            return new MemorySample(heapMemoryUsage.getUsed(), heapPeakBytes);
        }
    }

    private record AttemptResult(
            String scenario,
            long elapsedNanos,
            int changedRows,
            String status,
            String observation) {

        static AttemptResult success(String scenario, long elapsedNanos, int changedRows) {
            return new AttemptResult(scenario, elapsedNanos, changedRows, "OK", "");
        }

        static AttemptResult failure(String scenario, long elapsedNanos, Exception exception) {
            return new AttemptResult(
                    scenario,
                    elapsedNanos,
                    0,
                    "OBSERVED_FAILURE",
                    exception.getClass().getSimpleName());
        }
    }

    private static final class SqlCounter {
        private long count;

        void increment() {
            count++;
        }

        long count() {
            return count;
        }
    }

    private record Metric(
            int memoryMiB,
            int blockCount,
            String scenario,
            String status,
            long p50Nanos,
            long p95Nanos,
            long p99Nanos,
            long sqlCount,
            int responseBytes,
            long heapDeltaBytes,
            long heapPeakBytes,
            int changedRows,
            long walBytes,
            String observation) {

        static Metric fromMeasurements(
                int memoryMiB, int blockCount, String scenario, List<Measurement> measurements) {
            if (measurements.isEmpty()) {
                return observation(
                        memoryMiB, blockCount, scenario, Map.of("status", "NO_MEASUREMENTS"));
            }
            List<Long> elapsed =
                    measurements.stream().map(Measurement::elapsedNanos).sorted().toList();
            Measurement last = measurements.get(measurements.size() - 1);
            long sqlCount =
                    Math.round(
                            measurements.stream()
                                    .mapToLong(Measurement::sqlCount)
                                    .average()
                                    .orElse(0));
            long heapDelta =
                    measurements.stream()
                            .mapToLong(
                                    measurement ->
                                            measurement.heapAfterBytes()
                                                    - measurement.heapBeforeBytes())
                            .max()
                            .orElse(0);
            return new Metric(
                    memoryMiB,
                    blockCount,
                    scenario,
                    "OK",
                    percentile(elapsed, 50),
                    percentile(elapsed, 95),
                    percentile(elapsed, 99),
                    sqlCount,
                    last.responseBytes(),
                    heapDelta,
                    measurements.stream().mapToLong(Measurement::heapPeakBytes).max().orElse(0),
                    0,
                    0,
                    "responseHash=" + last.responseHash());
        }

        static Metric write(
                int memoryMiB,
                int blockCount,
                String scenario,
                long elapsedNanos,
                long sqlCount,
                int responseBytes,
                long heapDeltaBytes,
                long heapPeakBytes,
                int changedRows,
                long walBytes,
                String observation) {
            return new Metric(
                    memoryMiB,
                    blockCount,
                    scenario,
                    "OK",
                    elapsedNanos,
                    elapsedNanos,
                    elapsedNanos,
                    sqlCount,
                    responseBytes,
                    heapDeltaBytes,
                    heapPeakBytes,
                    changedRows,
                    walBytes,
                    observation);
        }

        static Metric concurrent(
                int memoryMiB,
                int blockCount,
                String scenario,
                long wallNanos,
                long walBytes,
                List<AttemptResult> results) {
            long failures =
                    results.stream().filter(result -> !"OK".equals(result.status())).count();
            int changedRows = results.stream().mapToInt(AttemptResult::changedRows).sum();
            List<Long> elapsed =
                    results.stream().map(AttemptResult::elapsedNanos).sorted().toList();
            String observation =
                    "attempts="
                            + results.size()
                            + ";failures="
                            + failures
                            + ";wallNanos="
                            + wallNanos;
            return new Metric(
                    memoryMiB,
                    blockCount,
                    scenario,
                    failures == 0 ? "OK" : "OBSERVED_FAILURE",
                    percentile(elapsed, 50),
                    percentile(elapsed, 95),
                    percentile(elapsed, 99),
                    results.size(),
                    0,
                    0,
                    0,
                    changedRows,
                    walBytes,
                    observation);
        }

        static Metric observation(
                int memoryMiB, int blockCount, String scenario, Map<String, String> details) {
            return new Metric(
                    memoryMiB,
                    blockCount,
                    scenario,
                    "OBSERVED",
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    details.toString());
        }

        static Metric logicalMismatch(
                int memoryMiB,
                int blockCount,
                String scenario,
                String expectedHash,
                Measurement measurement) {
            return new Metric(
                    memoryMiB,
                    blockCount,
                    scenario,
                    "LOGICAL_MISMATCH",
                    measurement.elapsedNanos(),
                    measurement.elapsedNanos(),
                    measurement.elapsedNanos(),
                    measurement.sqlCount(),
                    measurement.responseBytes(),
                    measurement.heapAfterBytes() - measurement.heapBeforeBytes(),
                    measurement.heapPeakBytes(),
                    0,
                    0,
                    "expected=" + expectedHash + ";actual=" + measurement.responseHash());
        }

        static Metric failure(int memoryMiB, int blockCount, String scenario, Exception exception) {
            return new Metric(
                    memoryMiB,
                    blockCount,
                    scenario,
                    "OBSERVED_FAILURE",
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    exception.getClass().getSimpleName()
                            + ":"
                            + Objects.toString(exception.getMessage(), ""));
        }

        static Metric resourceLimit(
                int memoryMiB, int blockCount, String scenario, Exception exception) {
            return new Metric(
                    memoryMiB,
                    blockCount,
                    scenario,
                    "RESOURCE_LIMIT_OOM",
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    exception.getClass().getSimpleName()
                            + ":"
                            + Objects.toString(exception.getMessage(), ""));
        }

        String toCsvRow() {
            return "%d,%d,%s,%s,%d,%d,%d,%d,%d,%d,%d,%d,%d,%s%n"
                    .formatted(
                            memoryMiB,
                            blockCount,
                            scenario,
                            status,
                            p50Nanos,
                            p95Nanos,
                            p99Nanos,
                            sqlCount,
                            responseBytes,
                            heapDeltaBytes,
                            heapPeakBytes,
                            changedRows,
                            walBytes,
                            observation.replace(',', ';').replace('\n', '|'));
        }

        private static long percentile(List<Long> sortedValues, int percentile) {
            int index = (int) Math.ceil((percentile / 100.0d) * sortedValues.size()) - 1;
            return sortedValues.get(Math.max(0, Math.min(index, sortedValues.size() - 1)));
        }
    }

    private static final class PerformanceReport {
        private final PerformanceProperties properties;
        private final List<Metric> metrics = new ArrayList<>();
        private final Map<String, String> plans = new LinkedHashMap<>();

        PerformanceReport(PerformanceProperties properties) {
            this.properties = properties;
        }

        void add(Metric metric) {
            metrics.add(metric);
        }

        void addPlan(int memoryMiB, int blockCount, String scenario, String plan) {
            plans.put(memoryMiB + "MiB-" + blockCount + "-" + scenario + ".json", plan);
        }

        int failedLogicalEquivalenceCount() {
            return (int)
                    metrics.stream()
                            .filter(metric -> "LOGICAL_MISMATCH".equals(metric.status()))
                            .count();
        }

        int invalidMatrixCount() {
            int invalid = 0;
            for (int memoryMiB : properties.memoryMiB()) {
                for (int blockCount : properties.sizes()) {
                    boolean completed =
                            metrics.stream()
                                    .anyMatch(
                                            metric ->
                                                    metric.memoryMiB() == memoryMiB
                                                            && metric.blockCount() == blockCount
                                                            && "RESOURCE_AFTER_MATRIX"
                                                                    .equals(metric.scenario()));
                    boolean classifiedResourceLimit =
                            metrics.stream()
                                    .anyMatch(
                                            metric ->
                                                    metric.memoryMiB() == memoryMiB
                                                            && metric.blockCount() == blockCount
                                                            && "RESOURCE_LIMIT_OOM"
                                                                    .equals(metric.status()));
                    if (!completed && !classifiedResourceLimit) {
                        invalid++;
                    }
                }
            }
            return invalid;
        }
    }

    private static final class ReportWriter {
        private final Path outputDirectory;

        ReportWriter(Path outputDirectory) {
            this.outputDirectory = outputDirectory;
        }

        void write(PerformanceReport report) throws IOException {
            Path planDirectory = outputDirectory.resolve("query-plans");
            Files.createDirectories(planDirectory);
            cleanPlanDirectory(planDirectory);
            writeEnvironment(report);
            writeMetrics(report);
            writeMetricsJson(report);
            writePlans(report, planDirectory);
            writeSummary(report);
        }

        private void cleanPlanDirectory(Path planDirectory) throws IOException {
            try (java.util.stream.Stream<Path> paths = Files.list(planDirectory)) {
                for (Path path : paths.toList()) {
                    Files.deleteIfExists(path);
                }
            }
        }

        private void writeEnvironment(PerformanceReport report) throws IOException {
            ObjectNode environment = OBJECT_MAPPER.createObjectNode();
            environment.put("javaVersion", System.getProperty("java.version"));
            environment.put("availableProcessors", Runtime.getRuntime().availableProcessors());
            environment.put("postgresImage", POSTGRES_IMAGE.asCanonicalNameString());
            ArrayNode sizes = environment.putArray("sizes");
            report.properties.sizes().forEach(sizes::add);
            ArrayNode memoryMiB = environment.putArray("memoryMiB");
            report.properties.memoryMiB().forEach(memoryMiB::add);
            environment.put("iterations", report.properties.iterations());
            environment.put("warmups", report.properties.warmups());
            ArrayNode concurrency = environment.putArray("concurrency");
            report.properties.concurrency().forEach(concurrency::add);
            environment.put("sharedBuffersRatio", "25% with 32MiB minimum");
            environment.put("workMem", "2MB");
            environment.put("maxConnections", 64);
            environment.put(
                    "datasetProfile", "block-rich-text-mention-heavy synthetic vertical slice");
            environment.put(
                    "measurementBoundary", "raw JDBC + app-side JSON assembly; not HTTP/JPA");
            Files.writeString(
                    outputDirectory.resolve("environment.json"),
                    OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(environment),
                    StandardCharsets.UTF_8);
        }

        private void writeMetrics(PerformanceReport report) throws IOException {
            StringBuilder builder = new StringBuilder();
            builder.append(
                    "memory_mib,block_count,scenario,status,p50_nanos,p95_nanos,p99_nanos,sql_count,"
                            + "response_bytes,heap_delta_bytes,heap_peak_bytes,changed_rows,wal_bytes,observation\n");
            for (Metric metric : report.metrics) {
                builder.append(metric.toCsvRow());
            }
            Files.writeString(
                    outputDirectory.resolve("metrics.csv"),
                    builder.toString(),
                    StandardCharsets.UTF_8);
        }

        private void writeMetricsJson(PerformanceReport report) throws IOException {
            Files.writeString(
                    outputDirectory.resolve("metrics.json"),
                    OBJECT_MAPPER
                            .writerWithDefaultPrettyPrinter()
                            .writeValueAsString(report.metrics),
                    StandardCharsets.UTF_8);
        }

        private void writePlans(PerformanceReport report, Path planDirectory) throws IOException {
            for (Map.Entry<String, String> entry : report.plans.entrySet()) {
                Files.writeString(
                        planDirectory.resolve(entry.getKey()),
                        entry.getValue(),
                        StandardCharsets.UTF_8);
            }
        }

        private void writeSummary(PerformanceReport report) throws IOException {
            long failures =
                    report.metrics.stream()
                            .filter(metric -> metric.status().contains("FAILURE"))
                            .count();
            long resourceLimits =
                    report.metrics.stream()
                            .filter(metric -> "RESOURCE_LIMIT_OOM".equals(metric.status()))
                            .count();
            StringBuilder builder = new StringBuilder();
            builder.append("# Notion 저장 모델 성능 실험 결과\n\n");
            builder.append("- 측정 행: ").append(report.metrics.size()).append("\n");
            builder.append("- 실패 관측 행: ").append(failures).append("\n");
            builder.append("- cgroup OOM 한계 행: ").append(resourceLimits).append("\n");
            builder.append("- 논리 JSON 불일치: ")
                    .append(report.failedLogicalEquivalenceCount())
                    .append("\n");
            builder.append("- 미완료 매트릭스: ").append(report.invalidMatrixCount()).append("\n");
            builder.append("- 쿼리 계획 파일: ").append(report.plans.size()).append("\n\n");
            builder.append("## 핵심 비교\n\n");
            builder.append("| DB 메모리 | Block | 비교 | 기준 p50(ms) | 개선 p50(ms) | 개선율 |\n");
            builder.append("| ---: | ---: | --- | ---: | ---: | ---: |\n");
            appendComparisonRows(builder, report, "N_PLUS_ONE", "BATCH");
            appendComparisonRows(builder, report, "BATCH", "SNAPSHOT_READ");
            appendComparisonRows(builder, report, "BATCH", "COMPACT");
            appendComparisonRows(builder, report, "INTEGER_REORDER", "POSITION_KEY_REORDER");
            builder.append("\n상세 수치는 `metrics.csv`, `metrics.json`, `query-plans/`를 확인한다.\n");
            builder.append(
                    "이 실험은 전체 A DDL을 생성하지만 Page/Block/Rich Text/Mention/Snapshot 수직 슬라이스만 "
                            + "채운다. Spring HTTP/JPA와 실제 Notion Importer 경로는 별도 2단계 실험 대상이다.\n");
            Files.writeString(
                    outputDirectory.resolve("summary.md"),
                    builder.toString(),
                    StandardCharsets.UTF_8);
        }

        private void appendComparisonRows(
                StringBuilder builder,
                PerformanceReport report,
                String baselineScenario,
                String improvedScenario) {
            for (int memoryMiB : report.properties.memoryMiB()) {
                for (int blockCount : report.properties.sizes()) {
                    Metric baseline = findMetric(report, memoryMiB, blockCount, baselineScenario);
                    Metric improved = findMetric(report, memoryMiB, blockCount, improvedScenario);
                    if (baseline == null || improved == null || baseline.p50Nanos() == 0) {
                        continue;
                    }
                    double improvement =
                            (1.0d - ((double) improved.p50Nanos() / baseline.p50Nanos())) * 100.0d;
                    builder.append(
                            "| %dMiB | %,d | %s → %s | %.3f | %.3f | %.1f%% |%n"
                                    .formatted(
                                            memoryMiB,
                                            blockCount,
                                            baselineScenario,
                                            improvedScenario,
                                            baseline.p50Nanos() / 1_000_000.0d,
                                            improved.p50Nanos() / 1_000_000.0d,
                                            improvement));
                }
            }
        }

        private Metric findMetric(
                PerformanceReport report, int memoryMiB, int blockCount, String scenario) {
            return report.metrics.stream()
                    .filter(
                            metric ->
                                    metric.memoryMiB() == memoryMiB
                                            && metric.blockCount() == blockCount
                                            && scenario.equals(metric.scenario()))
                    .findFirst()
                    .orElse(null);
        }
    }

    private static final class ActiveDatabase {
        private static String jdbcUrl;
        private static String username;
        private static String password;

        static void connect(String jdbcUrl, String username, String password) {
            ActiveDatabase.jdbcUrl = jdbcUrl;
            ActiveDatabase.username = username;
            ActiveDatabase.password = password;
        }

        static String jdbcUrl() {
            return jdbcUrl;
        }

        static String username() {
            return username;
        }

        static String password() {
            return password;
        }
    }
}
