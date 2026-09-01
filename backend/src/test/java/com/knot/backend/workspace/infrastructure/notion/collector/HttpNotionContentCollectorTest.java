package com.knot.backend.workspace.infrastructure.notion.collector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.knot.backend.workspace.application.NotionCollectionException;
import com.knot.backend.workspace.application.NotionCollectionFailureType;
import com.knot.backend.workspace.application.dto.result.CollectedNotionPage;
import com.knot.backend.workspace.application.dto.result.NotionCollectionResult;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class HttpNotionContentCollectorTest {
    private static final String ACCESS_CREDENTIAL = "access-secret-283";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private HttpServer server;
    private ExecutorService serverExecutor;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
        if (serverExecutor != null) {
            serverExecutor.shutdownNow();
        }
    }

    @DisplayName("Search와 Data Source query cursor를 소진하고 Block을 10단계 넘게 재귀 수집해 중복 없는 tree와 Markdown을 반환한다")
    @Test
    void collect_success_paginatesRecursesAndDeduplicates() throws Exception {
        // given
        SuccessfulNotionStub stub = new SuccessfulNotionStub();
        URI baseUri = startServer(stub);
        TestObserver observer = new TestObserver();
        HttpNotionContentCollector collector = collector(
                baseUri,
                Duration.ofSeconds(1),
                observer,
                ignored -> {
                }
        );

        // when
        NotionCollectionResult result = collector.collect(ACCESS_CREDENTIAL);

        // then
        assertThat(result.pages()).containsExactly(
                page(
                        "page-root",
                        null,
                        "Root",
                        rootMarkdown(),
                        0,
                        "https://notion.so/page-root"
                ),
                page(
                        "page-child",
                        "page-root",
                        "Child",
                        "Child body",
                        1,
                        "https://notion.so/page-child"
                ),
                page(
                        "data-source-1",
                        "page-root",
                        "Tasks",
                        2,
                        "https://notion.so/data-source-1"
                ),
                page(
                        "page-row",
                        "data-source-1",
                        "Row",
                        3,
                        "https://notion.so/page-row"
                )
        );
        assertThat(
                result.pages()
                        .getFirst()
                        .markdownContent()
        ).contains(
                "Root body",
                "depth 0",
                "depth 11"
        )
                .doesNotContain("Child body")
                .doesNotContain("raw-secret.png");
        assertThat(
                result.pages()
                        .get(1)
                        .markdownContent()
        ).isEqualTo("Child body");
        assertThat(stub.requestCount("/v1/pages/page-child")).isEqualTo(1);
        assertThat(stub.requestCount("/v1/pages/page-row")).isEqualTo(1);
        assertThat(stub.requestBodies())
                .anySatisfy(body -> assertThat(body).contains("\"start_cursor\":\"search-next\""))
                .anySatisfy(body -> assertThat(body).contains("\"start_cursor\":\"query-next\""));
        assertThat(stub.rawQueries()).anySatisfy(query -> assertThat(query).contains("start_cursor=blocks-next"));
        assertThat(stub.authorizationHeaders()).allMatch(value -> value.equals("Bearer " + ACCESS_CREDENTIAL));
        assertThat(stub.notionVersions()).containsOnly(HttpNotionContentCollector.NOTION_API_VERSION);
        assertThat(observer.blocks()).containsExactlyEntriesOf(
                Map.of(
                        "image",
                        1
                )
        );
        assertThat(observer.properties()).containsAllEntriesOf(
                Map.of(
                        "formula",
                        1,
                        "relation",
                        1
                )
        )
                .hasSize(2);
    }

    @DisplayName("429 Retry-After 초를 기다린 뒤 같은 요청을 재시도한다")
    @Test
    void collect_success_retriesRateLimitUsingRetryAfter() throws Exception {
        // given
        AtomicInteger requests = new AtomicInteger();
        URI baseUri = startServer(exchange -> {
            if (requests.incrementAndGet() == 1) {
                exchange.getResponseHeaders()
                        .add(
                                "Retry-After",
                                "2"
                        );
                respond(
                        exchange,
                        429,
                        "{\"message\":\"limited\"}"
                );
                return;
            }
            respondEmptyPage(exchange);
        });
        List<Duration> sleeps = new ArrayList<>();
        HttpNotionContentCollector collector = collector(
                baseUri,
                Duration.ofSeconds(1),
                new TestObserver(),
                sleeps::add
        );

        // when
        NotionCollectionResult result = collector.collect(ACCESS_CREDENTIAL);

        // then
        assertThat(result.pages()).isEmpty();
        assertThat(requests).hasValue(2);
        assertThat(sleeps).containsExactly(Duration.ofSeconds(2));
    }

    @DisplayName("5xx가 계속되면 최대 횟수까지만 재시도하고 일시 오류로 분류한다")
    @Test
    void collect_failure_stopsAtTemporaryRetryLimit() throws Exception {
        // given
        AtomicInteger requests = new AtomicInteger();
        URI baseUri = startServer(exchange -> {
            requests.incrementAndGet();
            respond(
                    exchange,
                    503,
                    "{\"message\":\"temporary raw response\"}"
            );
        });
        List<Duration> sleeps = new ArrayList<>();
        HttpNotionContentCollector collector = collector(
                baseUri,
                Duration.ofSeconds(1),
                new TestObserver(),
                sleeps::add
        );

        // when
        Throwable throwable = catchThrowable(() -> collector.collect(ACCESS_CREDENTIAL));

        // then
        assertFailureType(
                throwable,
                NotionCollectionFailureType.TEMPORARY
        );
        assertThat(requests).hasValue(HttpNotionContentCollector.MAX_ATTEMPTS);
        assertThat(sleeps).hasSize(HttpNotionContentCollector.MAX_ATTEMPTS - 1);
    }

    @DisplayName("요청 timeout도 최대 횟수까지만 재시도하고 일시 오류로 분류한다")
    @Test
    void collect_failure_retriesTimeoutWithinBound() throws Exception {
        // given
        AtomicInteger requests = new AtomicInteger();
        URI baseUri = startServer(exchange -> {
            requests.incrementAndGet();
            pause(Duration.ofMillis(100));
            respondEmptyPage(exchange);
        });
        HttpNotionContentCollector collector = collector(
                baseUri,
                Duration.ofMillis(20),
                new TestObserver(),
                ignored -> {
                }
        );

        // when
        Throwable throwable = catchThrowable(() -> collector.collect(ACCESS_CREDENTIAL));

        // then
        assertFailureType(
                throwable,
                NotionCollectionFailureType.TEMPORARY
        );
        assertThat(requests.get()).isBetween(
                2,
                HttpNotionContentCollector.MAX_ATTEMPTS
        );
    }

    @DisplayName("영구 4xx 응답은 재시도하지 않고 token과 raw response를 노출하지 않는다")
    @Test
    void collect_failure_doesNotRetryPermanentErrorOrExposeSecrets() throws Exception {
        // given
        AtomicInteger requests = new AtomicInteger();
        URI baseUri = startServer(exchange -> {
            requests.incrementAndGet();
            respond(
                    exchange,
                    400,
                    "{\"message\":\"" + ACCESS_CREDENTIAL + " raw payload\"}"
            );
        });
        HttpNotionContentCollector collector = collector(
                baseUri,
                Duration.ofSeconds(1),
                new TestObserver(),
                ignored -> {
                }
        );

        // when
        Throwable throwable = catchThrowable(() -> collector.collect(ACCESS_CREDENTIAL));

        // then
        assertFailureType(
                throwable,
                NotionCollectionFailureType.INVALID_REQUEST
        );
        assertThat(requests).hasValue(1);
        assertThat(throwable).hasMessageNotContaining(ACCESS_CREDENTIAL)
                .hasMessageNotContaining("raw payload")
                .hasNoCause();
    }

    @DisplayName("수집한 Page 중 하나라도 최종 실패하면 부분 결과는 버리고 이미 건너뛴 block은 관측한다")
    @Test
    void collect_failure_discardsPartialPages() throws Exception {
        // given
        TestObserver observer = new TestObserver();
        AtomicInteger rootRequests = new AtomicInteger();
        URI baseUri = startServer(exchange -> {
            switch (exchange.getRequestURI()
                    .getPath()) {
                case "/v1/search" -> respond(
                        exchange,
                        200,
                        pageList("""
                                {"object":"page","id":"page-root"},
                                {"object":"page","id":"page-broken"}
                                """)
                );
                case "/v1/pages/page-root" -> {
                    rootRequests.incrementAndGet();
                    respond(
                            exchange,
                            200,
                            pageResponse(
                                    "page-root",
                                    "Root",
                                    "{\"type\":\"workspace\",\"workspace\":true}",
                                    ""
                            )
                    );
                }
                case "/v1/blocks/page-root/children" -> respond(
                        exchange,
                        200,
                        blockList("""
                                {"id":"image-1","type":"image","has_children":false,"image":{}}
                                """)
                );
                case "/v1/pages/page-broken" -> respond(
                        exchange,
                        404,
                        "{\"message\":\"missing\"}"
                );
                default -> respond(
                        exchange,
                        500,
                        "{}"
                );
            }
        });
        HttpNotionContentCollector collector = collector(
                baseUri,
                Duration.ofSeconds(1),
                observer,
                ignored -> {
                }
        );

        // when
        Throwable throwable = catchThrowable(() -> collector.collect(ACCESS_CREDENTIAL));

        // then
        assertFailureType(
                throwable,
                NotionCollectionFailureType.NOT_FOUND
        );
        assertThat(rootRequests).hasValue(1);
        assertThat(observer.blocks()).containsExactlyEntriesOf(
                Map.of(
                        "image",
                        1
                )
        );
        assertThat(observer.properties()).isEmpty();
    }

    @DisplayName("pagination cursor가 반복되면 응답 오류로 중단한다")
    @Test
    void collect_failure_rejectsPaginationCursorLoop() throws Exception {
        // given
        URI baseUri = startServer(
                exchange -> respond(
                        exchange,
                        200,
                        """
                                {
                                  "results":[],
                                  "has_more":true,
                                  "next_cursor":"same-cursor"
                                }
                                """
                )
        );
        HttpNotionContentCollector collector = collector(
                baseUri,
                Duration.ofSeconds(1),
                new TestObserver(),
                ignored -> {
                }
        );

        // when
        Throwable throwable = catchThrowable(() -> collector.collect(ACCESS_CREDENTIAL));

        // then
        assertFailureType(
                throwable,
                NotionCollectionFailureType.INVALID_RESPONSE
        );
    }

    private CollectedNotionPage page(
            String id,
            String parentId,
            String title,
            int position,
            String url
    ) {
        return page(
                id,
                parentId,
                title,
                "",
                position,
                url
        );
    }

    private CollectedNotionPage page(
            String id,
            String parentId,
            String title,
            String markdownContent,
            int position,
            String url
    ) {
        return new CollectedNotionPage(
                id,
                parentId,
                title,
                markdownContent,
                position,
                url
        );
    }

    private String rootMarkdown() {
        StringBuilder markdown = new StringBuilder("Root body\n\n");
        for (int depth = 0; depth <= 11; depth++) {
            if (depth > 0) {
                markdown.append('\n');
            }
            markdown.append("depth ")
                    .append(depth);
        }
        return markdown.toString();
    }

    private HttpNotionContentCollector collector(
            URI baseUri,
            Duration timeout,
            NotionCollectionObserver observer,
            NotionRetrySleeper sleeper
    ) {
        return new HttpNotionContentCollector(
                HttpClient.newBuilder()
                        .connectTimeout(timeout)
                        .build(),
                objectMapper,
                baseUri,
                timeout,
                observer,
                sleeper
        );
    }

    private URI startServer(HttpHandler handler) throws IOException {
        server = HttpServer.create(
                new InetSocketAddress(0),
                0
        );
        serverExecutor = Executors.newCachedThreadPool();
        server.setExecutor(serverExecutor);
        server.createContext(
                "/",
                handler
        );
        server.start();
        return URI.create(
                "http://localhost:" + server.getAddress()
                        .getPort() + "/v1"
        );
    }

    private void respondEmptyPage(HttpExchange exchange) throws IOException {
        respond(
                exchange,
                200,
                pageList("")
        );
    }

    private void respond(
            HttpExchange exchange,
            int status,
            String body
    ) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders()
                .add(
                        "Content-Type",
                        "application/json"
                );
        exchange.sendResponseHeaders(
                status,
                bytes.length
        );
        exchange.getResponseBody()
                .write(bytes);
        exchange.close();
    }

    private String pageList(String results) {
        return """
                {
                  "results":[%s],
                  "has_more":false,
                  "next_cursor":null
                }
                """.formatted(results);
    }

    private String blockList(String results) {
        return pageList(results);
    }

    private String pageResponse(
            String id,
            String title,
            String parent,
            String additionalProperties
    ) {
        return """
                {
                  "object":"page",
                  "id":"%s",
                  "parent":%s,
                  "url":"https://notion.so/%s",
                  "properties":{
                    "Name":{
                      "type":"title",
                      "title":[{"plain_text":"%s"}]
                    }%s
                  }
                }
                """.formatted(
                id,
                parent,
                id,
                title,
                additionalProperties
        );
    }

    private void pause(Duration duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException exception) {
            Thread.currentThread()
                    .interrupt();
        }
    }

    private void assertFailureType(
            Throwable throwable,
            NotionCollectionFailureType failureType
    ) {
        assertThat(throwable).isInstanceOfSatisfying(
                NotionCollectionException.class,
                exception -> assertThat(exception.getFailureType()).isEqualTo(failureType)
        );
    }

    private final class SuccessfulNotionStub implements HttpHandler {
        private final Map<String, AtomicInteger> requestCounts = new ConcurrentHashMap<>();
        private final List<String> requestBodies = new CopyOnWriteArrayList<>();
        private final List<String> rawQueries = new CopyOnWriteArrayList<>();
        private final List<String> authorizationHeaders = new CopyOnWriteArrayList<>();
        private final List<String> notionVersions = new CopyOnWriteArrayList<>();

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI()
                    .getPath();
            requestCounts.computeIfAbsent(
                    path,
                    ignored -> new AtomicInteger()
            )
                    .incrementAndGet();
            authorizationHeaders.add(
                    exchange.getRequestHeaders()
                            .getFirst("Authorization")
            );
            notionVersions.add(
                    exchange.getRequestHeaders()
                            .getFirst("Notion-Version")
            );
            String body = new String(
                    exchange.getRequestBody()
                            .readAllBytes(),
                    StandardCharsets.UTF_8
            );
            if (!body.isBlank()) {
                requestBodies.add(body);
            }
            String query = exchange.getRequestURI()
                    .getRawQuery();
            if (query != null) {
                rawQueries.add(query);
            }
            route(
                    exchange,
                    path,
                    body,
                    query
            );
        }

        private void route(
                HttpExchange exchange,
                String path,
                String body,
                String query
        ) throws IOException {
            switch (path) {
                case "/v1/search" -> search(
                        exchange,
                        body
                );
                case "/v1/pages/page-root" -> respond(
                        exchange,
                        200,
                        pageResponse(
                                "page-root",
                                "Root",
                                "{\"type\":\"workspace\",\"workspace\":true}",
                                ""
                        )
                );
                case "/v1/pages/page-child" -> respond(
                        exchange,
                        200,
                        pageResponse(
                                "page-child",
                                "Child",
                                "{\"type\":\"page_id\",\"page_id\":\"page-root\"}",
                                ""
                        )
                );
                case "/v1/pages/page-row" -> respond(
                        exchange,
                        200,
                        pageResponse(
                                "page-row",
                                "Row",
                                "{\"type\":\"data_source_id\",\"data_source_id\":\"data-source-1\"}",
                                rowComplexProperties()
                        )
                );
                case "/v1/blocks/page-root/children" -> rootBlocks(
                        exchange,
                        query
                );
                case "/v1/blocks/page-child/children" -> respond(
                        exchange,
                        200,
                        blockList("""
                                {
                                  "id":"block-child-1",
                                  "type":"paragraph",
                                  "has_children":false,
                                  "paragraph":{"rich_text":[{"plain_text":"Child body"}]}
                                }
                                """)
                );
                case "/v1/blocks/page-row/children" -> respond(
                        exchange,
                        200,
                        blockList("")
                );
                case "/v1/data_sources/data-source-1/query" -> dataSourceRows(
                        exchange,
                        body
                );
                default -> deepBlocks(
                        exchange,
                        path
                );
            }
        }

        private void search(
                HttpExchange exchange,
                String body
        ) throws IOException {
            JsonNode request = readBody(body);
            JsonNode cursor = request.get("start_cursor");
            if (cursor == null) {
                respond(
                        exchange,
                        200,
                        firstSearchPage()
                );
                return;
            }
            respond(
                    exchange,
                    200,
                    secondSearchPage()
            );
        }

        private String firstSearchPage() {
            return """
                    {
                      "results":[{"object":"page","id":"page-root"}],
                      "has_more":true,
                      "next_cursor":"search-next"
                    }
                    """;
        }

        private String secondSearchPage() {
            return """
                    {
                      "results":[
                        {"object":"page","id":"page-child"},
                        {
                          "object":"data_source",
                          "id":"data-source-1",
                          "parent":{"type":"database_id","database_id":"database-1"},
                          "url":"https://notion.so/data-source-1",
                          "title":[{"plain_text":"Tasks"}]
                        }
                      ],
                      "has_more":false,
                      "next_cursor":null
                    }
                    """;
        }

        private void rootBlocks(
                HttpExchange exchange,
                String query
        ) throws IOException {
            if (query == null || !query.contains("start_cursor")) {
                respond(
                        exchange,
                        200,
                        """
                                {
                                  "results":[
                                    {
                                      "id":"block-root-1",
                                      "type":"paragraph",
                                      "has_children":false,
                                      "paragraph":{"rich_text":[{"plain_text":"Root body"}]}
                                    },
                                    {
                                      "id":"page-child",
                                      "type":"child_page",
                                      "has_children":true,
                                      "child_page":{"title":"Child"}
                                    }
                                  ],
                                  "has_more":true,
                                  "next_cursor":"blocks-next"
                                }
                                """
                );
                return;
            }
            respond(
                    exchange,
                    200,
                    """
                            {
                              "results":[
                                {
                                  "id":"database-1",
                                  "type":"child_database",
                                  "has_children":false,
                                  "child_database":{"title":"Tasks"}
                                },
                                {
                                  "id":"deep-0",
                                  "type":"paragraph",
                                  "has_children":true,
                                  "paragraph":{"rich_text":[{"plain_text":"depth 0"}]}
                                },
                                {
                                  "id":"image-1",
                                  "type":"image",
                                  "has_children":false,
                                  "image":{"type":"external","external":{"url":"https://static.test/raw-secret.png"}}
                                }
                              ],
                              "has_more":false,
                              "next_cursor":null
                            }
                            """
            );
        }

        private void dataSourceRows(
                HttpExchange exchange,
                String body
        ) throws IOException {
            JsonNode request = readBody(body);
            if (request.get("start_cursor") == null) {
                respond(
                        exchange,
                        200,
                        """
                                {
                                  "results":[{"object":"page","id":"page-row"}],
                                  "has_more":true,
                                  "next_cursor":"query-next"
                                }
                                """
                );
                return;
            }
            respond(
                    exchange,
                    200,
                    pageList("{\"object\":\"page\",\"id\":\"page-row\"}")
            );
        }

        private void deepBlocks(
                HttpExchange exchange,
                String path
        ) throws IOException {
            String prefix = "/v1/blocks/deep-";
            String suffix = "/children";
            if (!path.startsWith(prefix) || !path.endsWith(suffix)) {
                respond(
                        exchange,
                        404,
                        "{}"
                );
                return;
            }
            int depth = Integer.parseInt(
                    path.substring(
                            prefix.length(),
                            path.length() - suffix.length()
                    )
            );
            int childDepth = depth + 1;
            if (childDepth > 11) {
                respond(
                        exchange,
                        200,
                        blockList("")
                );
                return;
            }
            respond(
                    exchange,
                    200,
                    blockList(
                            """
                                    {
                                      "id":"deep-%d",
                                      "type":"paragraph",
                                      "has_children":%s,
                                      "paragraph":{"rich_text":[{"plain_text":"depth %d"}]}
                                    }
                                    """.formatted(
                                    childDepth,
                                    childDepth < 11,
                                    childDepth
                            )
                    )
            );
        }

        private JsonNode readBody(String body) throws JacksonException {
            return objectMapper.readTree(body);
        }

        private String rowComplexProperties() {
            return """
                    ,"Relation":{"type":"relation","relation":[{"id":"raw-related-id"}]},
                    "Formula":{"type":"formula","formula":{"type":"string","string":"raw-secret"}}
                    """.strip();
        }

        private int requestCount(String path) {
            AtomicInteger count = requestCounts.get(path);
            return count == null ? 0 : count.get();
        }

        private List<String> requestBodies() {
            return requestBodies;
        }

        private List<String> rawQueries() {
            return rawQueries;
        }

        private List<String> authorizationHeaders() {
            return authorizationHeaders;
        }

        private List<String> notionVersions() {
            return notionVersions;
        }
    }

    private static final class TestObserver implements NotionCollectionObserver {
        private final Map<String, Integer> blocks = new ConcurrentHashMap<>();
        private final Map<String, Integer> properties = new ConcurrentHashMap<>();

        @Override
        public void recordSkippedBlock(
                String blockType,
                int count
        ) {
            blocks.merge(
                    blockType,
                    count,
                    Integer::sum
            );
        }

        @Override
        public void recordSkippedProperty(
                String propertyType,
                int count
        ) {
            properties.merge(
                    propertyType,
                    count,
                    Integer::sum
            );
        }

        private Map<String, Integer> blocks() {
            return blocks;
        }

        private Map<String, Integer> properties() {
            return properties;
        }
    }
}
