package com.knot.backend.workspace.infrastructure.notion.collector;

import static com.knot.backend.workspace.infrastructure.notion.collector.NotionJson.invalidResponse;
import static com.knot.backend.workspace.infrastructure.notion.collector.NotionJson.requireArray;
import static com.knot.backend.workspace.infrastructure.notion.collector.NotionJson.requireObject;
import static com.knot.backend.workspace.infrastructure.notion.collector.NotionJson.requiredNonBlankString;

import com.knot.backend.workspace.application.NotionCollectionException;
import com.knot.backend.workspace.application.NotionCollectionFailureType;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

final class NotionApiClient {
    static final String NOTION_API_VERSION = "2026-03-11";
    static final int MAX_ATTEMPTS = 5;
    private static final int PAGE_SIZE = 100;
    private static final Duration INITIAL_RETRY_DELAY = Duration.ofMillis(200);
    private static final Duration MAX_RETRY_DELAY = Duration.ofSeconds(30);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final URI apiBaseUri;
    private final Duration requestTimeout;
    private final NotionRetrySleeper sleeper;

    NotionApiClient(
            HttpClient httpClient,
            ObjectMapper objectMapper,
            URI apiBaseUri,
            Duration requestTimeout,
            NotionRetrySleeper sleeper
    ) {
        this.httpClient = Objects.requireNonNull(
                httpClient,
                "httpClient"
        );
        this.objectMapper = Objects.requireNonNull(
                objectMapper,
                "objectMapper"
        );
        this.apiBaseUri = validateApiBaseUri(apiBaseUri);
        this.requestTimeout = validateRequestTimeout(requestTimeout);
        this.sleeper = Objects.requireNonNull(
                sleeper,
                "sleeper"
        );
    }

    List<JsonNode> search(String accessCredential) {
        return fetchPaginated(
                "POST",
                endpoint("search"),
                accessCredential,
                Map.of()
        );
    }

    JsonNode retrievePage(
            String pageId,
            String accessCredential
    ) {
        return fetchObject(
                endpoint(
                        "pages",
                        pageId
                ),
                accessCredential
        );
    }

    List<JsonNode> retrieveBlockChildren(
            String blockId,
            String accessCredential
    ) {
        return fetchPaginated(
                "GET",
                endpoint(
                        "blocks",
                        blockId,
                        "children"
                ),
                accessCredential,
                Map.of()
        );
    }

    List<JsonNode> queryDataSource(
            String dataSourceId,
            String accessCredential
    ) {
        return fetchPaginated(
                "POST",
                endpoint(
                        "data_sources",
                        dataSourceId,
                        "query"
                ),
                accessCredential,
                Map.of()
        );
    }

    private JsonNode fetchObject(
            URI uri,
            String accessCredential
    ) {
        HttpResponse<String> response = sendWithRetry(
                requestBuilder(
                        uri,
                        accessCredential
                ).GET()
                        .build()
        );
        try {
            JsonNode body = objectMapper.readTree(response.body());
            requireObject(body);
            return body;
        } catch (JacksonException exception) {
            throw invalidResponse();
        }
    }

    private List<JsonNode> fetchPaginated(
            String method,
            URI uri,
            String accessCredential,
            Map<String, Object> baseBody
    ) {
        List<JsonNode> results = new ArrayList<>();
        Set<String> seenCursors = new HashSet<>();
        String cursor = null;
        do {
            HttpRequest request = paginatedRequest(
                    method,
                    uri,
                    accessCredential,
                    baseBody,
                    cursor
            );
            JsonNode response = parsePaginatedResponse(sendWithRetry(request));
            appendResults(
                    response,
                    results
            );
            cursor = nextCursor(
                    response,
                    seenCursors
            );
        } while (cursor != null);
        return results;
    }

    private HttpRequest paginatedRequest(
            String method,
            URI uri,
            String accessCredential,
            Map<String, Object> baseBody,
            String cursor
    ) {
        if ("GET".equals(method)) {
            return paginatedGetRequest(
                    uri,
                    accessCredential,
                    cursor
            );
        }
        return paginatedPostRequest(
                uri,
                accessCredential,
                baseBody,
                cursor
        );
    }

    private HttpRequest paginatedGetRequest(
            URI uri,
            String accessCredential,
            String cursor
    ) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUri(uri)
                .queryParam(
                        "page_size",
                        PAGE_SIZE
                );
        if (cursor != null) {
            builder.queryParam(
                    "start_cursor",
                    cursor
            );
        }
        return requestBuilder(
                builder.build()
                        .encode()
                        .toUri(),
                accessCredential
        ).GET()
                .build();
    }

    private HttpRequest paginatedPostRequest(
            URI uri,
            String accessCredential,
            Map<String, Object> baseBody,
            String cursor
    ) {
        Map<String, Object> body = new LinkedHashMap<>(baseBody);
        body.put(
                "page_size",
                PAGE_SIZE
        );
        if (cursor != null) {
            body.put(
                    "start_cursor",
                    cursor
            );
        }
        return requestBuilder(
                uri,
                accessCredential
        ).POST(HttpRequest.BodyPublishers.ofString(serialize(body)))
                .build();
    }

    private JsonNode parsePaginatedResponse(HttpResponse<String> response) {
        try {
            JsonNode body = objectMapper.readTree(response.body());
            requireObject(body);
            return body;
        } catch (JacksonException exception) {
            throw invalidResponse();
        }
    }

    private void appendResults(
            JsonNode response,
            List<JsonNode> results
    ) {
        JsonNode pageResults = response.get("results");
        requireArray(pageResults);
        for (JsonNode result : pageResults) {
            requireObject(result);
            results.add(result);
        }
    }

    private String nextCursor(
            JsonNode response,
            Set<String> seenCursors
    ) {
        JsonNode hasMore = response.get("has_more");
        if (hasMore == null || !hasMore.isBoolean()) {
            throw invalidResponse();
        }
        if (!hasMore.asBoolean()) {
            return null;
        }
        String cursor = requiredNonBlankString(
                response,
                "next_cursor"
        );
        if (cursor.length() > 2_048 || !seenCursors.add(cursor)) {
            throw invalidResponse();
        }
        return cursor;
    }

    private HttpResponse<String> sendWithRetry(HttpRequest request) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                HttpResponse<String> response = httpClient.send(
                        request,
                        HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
                );
                if (isSuccess(response.statusCode())) {
                    return response;
                }
                if (!isRetryable(response.statusCode()) || attempt == MAX_ATTEMPTS) {
                    throw failureForStatus(response.statusCode());
                }
                sleepBeforeRetry(
                        retryDelay(
                                response,
                                attempt
                        )
                );
            } catch (HttpTimeoutException exception) {
                retryAfterIoFailure(attempt);
            } catch (IOException exception) {
                retryAfterIoFailure(attempt);
            } catch (InterruptedException exception) {
                Thread.currentThread()
                        .interrupt();
                throw temporaryFailure();
            }
        }
        throw temporaryFailure();
    }

    private void retryAfterIoFailure(int attempt) {
        if (attempt == MAX_ATTEMPTS) {
            throw temporaryFailure();
        }
        sleepBeforeRetry(exponentialDelay(attempt));
    }

    private void sleepBeforeRetry(Duration duration) {
        try {
            sleeper.sleep(duration);
        } catch (InterruptedException exception) {
            Thread.currentThread()
                    .interrupt();
            throw temporaryFailure();
        }
    }

    private Duration retryDelay(
            HttpResponse<String> response,
            int attempt
    ) {
        if (response.statusCode() != 429) {
            return exponentialDelay(attempt);
        }
        String retryAfter = response.headers()
                .firstValue("Retry-After")
                .orElse(null);
        if (retryAfter == null) {
            return exponentialDelay(attempt);
        }
        try {
            long seconds = Long.parseLong(retryAfter);
            if (seconds < 0) {
                return exponentialDelay(attempt);
            }
            Duration requestedDelay = Duration.ofSeconds(seconds);
            if (requestedDelay.compareTo(MAX_RETRY_DELAY) > 0) {
                throw new NotionCollectionException(NotionCollectionFailureType.RATE_LIMITED);
            }
            return requestedDelay;
        } catch (NumberFormatException exception) {
            return exponentialDelay(attempt);
        }
    }

    private Duration exponentialDelay(int attempt) {
        long multiplier = 1L << Math.min(
                attempt - 1,
                20
        );
        return min(
                INITIAL_RETRY_DELAY.multipliedBy(multiplier),
                MAX_RETRY_DELAY
        );
    }

    private Duration min(
            Duration left,
            Duration right
    ) {
        return left.compareTo(right) <= 0 ? left : right;
    }

    private boolean isSuccess(int statusCode) {
        return statusCode >= 200 && statusCode < 300;
    }

    private boolean isRetryable(int statusCode) {
        return statusCode == 429 || statusCode >= 500 && statusCode < 600;
    }

    private NotionCollectionException failureForStatus(int statusCode) {
        if (statusCode == 401 || statusCode == 403) {
            return new NotionCollectionException(NotionCollectionFailureType.ACCESS_DENIED);
        }
        if (statusCode == 404) {
            return new NotionCollectionException(NotionCollectionFailureType.NOT_FOUND);
        }
        if (statusCode == 429) {
            return new NotionCollectionException(NotionCollectionFailureType.RATE_LIMITED);
        }
        if (statusCode >= 500 && statusCode < 600) {
            return temporaryFailure();
        }
        if (statusCode >= 400 && statusCode < 500) {
            return new NotionCollectionException(NotionCollectionFailureType.INVALID_REQUEST);
        }
        return invalidResponse();
    }

    private HttpRequest.Builder requestBuilder(
            URI uri,
            String accessCredential
    ) {
        try {
            return HttpRequest.newBuilder(uri)
                    .timeout(requestTimeout)
                    .header(
                            HttpHeaders.AUTHORIZATION,
                            "Bearer " + accessCredential
                    )
                    .header(
                            HttpHeaders.ACCEPT,
                            MediaType.APPLICATION_JSON_VALUE
                    )
                    .header(
                            HttpHeaders.CONTENT_TYPE,
                            MediaType.APPLICATION_JSON_VALUE
                    )
                    .header(
                            "Notion-Version",
                            NOTION_API_VERSION
                    );
        } catch (IllegalArgumentException exception) {
            throw new NotionCollectionException(NotionCollectionFailureType.INVALID_REQUEST);
        }
    }

    private String serialize(Map<String, Object> body) {
        try {
            return objectMapper.writeValueAsString(body);
        } catch (JacksonException exception) {
            throw invalidResponse();
        }
    }

    private URI endpoint(String... pathSegments) {
        try {
            return UriComponentsBuilder.fromUri(apiBaseUri)
                    .pathSegment(pathSegments)
                    .build()
                    .encode()
                    .toUri();
        } catch (IllegalArgumentException exception) {
            throw invalidResponse();
        }
    }

    private URI validateApiBaseUri(URI value) {
        if (value == null || !value.isAbsolute() || value.getHost() == null || value.getUserInfo() != null
                || !("https".equalsIgnoreCase(value.getScheme()) || "http".equalsIgnoreCase(value.getScheme()))) {
            throw new IllegalArgumentException("apiBaseUri");
        }
        return value;
    }

    private Duration validateRequestTimeout(Duration value) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException("requestTimeout");
        }
        return value;
    }

    private NotionCollectionException temporaryFailure() {
        return new NotionCollectionException(NotionCollectionFailureType.TEMPORARY);
    }
}
