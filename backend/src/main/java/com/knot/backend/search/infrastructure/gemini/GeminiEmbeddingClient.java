package com.knot.backend.search.infrastructure.gemini;

import com.knot.backend.search.application.DocumentEmbeddingClient;
import com.knot.backend.search.application.EmbeddingProperties;
import com.knot.backend.search.application.EmbeddingTask;
import com.knot.backend.search.domain.SearchErrorCode;
import com.knot.backend.search.domain.SearchException;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Gemini Embedding API(batchEmbedContents) 어댑터. 색인은 RETRIEVAL_DOCUMENT, 질의는
 * RETRIEVAL_QUERY로 보내고, outputDimensionality를 V13 차원(llm.embedding.dimensions)에
 * 맞춘 뒤 응답을 L2 정규화한다. gemini-embedding-001은 3,072 미만 벡터를 정규화해 주지 않는다(로드맵 Q35).
 * 색인 배치가 429·503이면 llm.gemini.retry-*대로 지수 백오프 재시도하고, 질의는 재시도하지 않는다(로드맵 Q42).
 */
public final class GeminiEmbeddingClient implements DocumentEmbeddingClient {
    private static final Logger log = LoggerFactory.getLogger(GeminiEmbeddingClient.class);
    private static final Duration MAX_RETRY_DELAY = Duration.ofSeconds(60);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final GeminiEmbeddingProperties properties;
    private final EmbeddingProperties embeddingProperties;
    private final GeminiRetrySleeper sleeper;

    public GeminiEmbeddingClient(
            HttpClient httpClient,
            ObjectMapper objectMapper,
            GeminiEmbeddingProperties properties,
            EmbeddingProperties embeddingProperties
    ) {
        this(
                httpClient,
                objectMapper,
                properties,
                embeddingProperties,
                Thread::sleep
        );
    }

    GeminiEmbeddingClient(
            HttpClient httpClient,
            ObjectMapper objectMapper,
            GeminiEmbeddingProperties properties,
            EmbeddingProperties embeddingProperties,
            GeminiRetrySleeper sleeper
    ) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.embeddingProperties = embeddingProperties;
        this.sleeper = sleeper;
    }

    @Override
    public List<double[]> embed(
            List<String> texts,
            EmbeddingTask task
    ) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }
        validateConfiguration();
        int maxAttempts = task == EmbeddingTask.DOCUMENT ? properties.retryMaxAttempts() : 1;
        Duration delay = properties.retryInitialDelay();
        for (int attempt = 1;; attempt++) {
            HttpResponse<String> response = send(
                    texts,
                    task
            );
            int status = response.statusCode();
            if (status >= 200 && status < 300) {
                return parse(
                        response.body(),
                        texts.size()
                );
            }
            if (!isRetryable(status) || attempt >= maxAttempts) {
                throw failed(response);
            }
            log.warn(
                    "Gemini embeddings API 요청 실패 status={} errorStatus={} attempt={}/{} retryIn={}",
                    status,
                    errorStatus(response.body()),
                    attempt,
                    maxAttempts,
                    delay
            );
            sleepBeforeRetry(delay);
            delay = min(
                    delay.multipliedBy(2),
                    MAX_RETRY_DELAY
            );
        }
    }

    // llm.embedding.model은 openai-compatible 전용이라 여기서는 차원만 검사한다.
    private void validateConfiguration() {
        properties.validate();
        if (embeddingProperties.dimensions() <= 0) {
            throw new SearchException(SearchErrorCode.SEARCH_CONFIGURATION_INVALID);
        }
    }

    private HttpResponse<String> send(
            List<String> texts,
            EmbeddingTask task
    ) {
        try {
            return httpClient.send(
                    request(
                            texts,
                            task
                    ),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );
        } catch (InterruptedException exception) {
            Thread.currentThread()
                    .interrupt();
            throw failed(exception);
        } catch (IOException | JacksonException exception) {
            throw failed(exception);
        }
    }

    private List<double[]> parse(
            String body,
            int expectedSize
    ) {
        try {
            return parseEmbeddings(
                    body,
                    expectedSize
            );
        } catch (SearchException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw failed(exception);
        }
    }

    // 429(quota)·503(unavailable)만 지나가는 실패로 본다. 4xx 나머지·IO 오류·차원 불일치는 재시도해도 같다.
    private static boolean isRetryable(int status) {
        return status == 429 || status == 503;
    }

    private void sleepBeforeRetry(Duration delay) {
        try {
            sleeper.sleep(delay);
        } catch (InterruptedException exception) {
            Thread.currentThread()
                    .interrupt();
            throw failed(exception);
        }
    }

    private static Duration min(
            Duration left,
            Duration right
    ) {
        return left.compareTo(right) <= 0 ? left : right;
    }

    private HttpRequest request(
            List<String> texts,
            EmbeddingTask task
    ) throws JacksonException {
        List<Map<String, Object>> requests = new ArrayList<>(texts.size());
        for (String text : texts) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put(
                    "model",
                    properties.modelResourceName()
            );
            item.put(
                    "content",
                    Map.of(
                            "parts",
                            List.of(
                                    Map.of(
                                            "text",
                                            text == null ? "" : text
                                    )
                            )
                    )
            );
            item.put(
                    "taskType",
                    taskType(task)
            );
            item.put(
                    "outputDimensionality",
                    embeddingProperties.dimensions()
            );
            requests.add(item);
        }
        Map<String, Object> payload = Map.of(
                "requests",
                requests
        );
        return HttpRequest.newBuilder(properties.batchEmbedContentsUri())
                .timeout(properties.requestTimeout())
                .header(
                        "x-goog-api-key",
                        properties.apiKey()
                )
                .header(
                        "Content-Type",
                        "application/json"
                )
                .header(
                        "Accept",
                        "application/json"
                )
                .POST(
                        HttpRequest.BodyPublishers.ofString(
                                objectMapper.writeValueAsString(payload),
                                StandardCharsets.UTF_8
                        )
                )
                .build();
    }

    private static String taskType(EmbeddingTask task) {
        return task == EmbeddingTask.QUERY ? "RETRIEVAL_QUERY" : "RETRIEVAL_DOCUMENT";
    }

    private List<double[]> parseEmbeddings(
            String body,
            int expectedSize
    ) throws JacksonException {
        JsonNode embeddings = objectMapper.readTree(body)
                .get("embeddings");
        if (embeddings == null || !embeddings.isArray() || embeddings.size() != expectedSize) {
            throw new SearchException(SearchErrorCode.SEARCH_PROVIDER_FAILED);
        }
        List<double[]> result = new ArrayList<>(expectedSize);
        for (JsonNode item : embeddings) {
            JsonNode values = item.get("values");
            if (values == null || !values.isArray() || values.size() != embeddingProperties.dimensions()) {
                throw new SearchException(SearchErrorCode.SEARCH_PROVIDER_FAILED);
            }
            double[] embedding = new double[values.size()];
            for (int index = 0; index < values.size(); index++) {
                JsonNode value = values.get(index);
                if (!value.isNumber()) {
                    throw new SearchException(SearchErrorCode.SEARCH_PROVIDER_FAILED);
                }
                embedding[index] = value.asDouble();
            }
            result.add(normalize(embedding));
        }
        return List.copyOf(result);
    }

    private static double[] normalize(double[] embedding) {
        double squaredSum = 0.0;
        for (double value : embedding) {
            squaredSum += value * value;
        }
        double norm = Math.sqrt(squaredSum);
        if (!(norm > 0.0) || !Double.isFinite(norm)) {
            throw new SearchException(SearchErrorCode.SEARCH_PROVIDER_FAILED);
        }
        for (int index = 0; index < embedding.length; index++) {
            embedding[index] /= norm;
        }
        return embedding;
    }

    // 키가 잘못된 경우(401·403)만 설정 오류로 구분하고 나머지는 provider 실패로 본다(로드맵 Q38). 본문은
    // error.status만 남긴다.
    private SearchException failed(HttpResponse<String> response) {
        int status = response.statusCode();
        log.warn(
                "Gemini embeddings API 요청 실패 status={} errorStatus={}",
                status,
                errorStatus(response.body())
        );
        if (status == 401 || status == 403) {
            return new SearchException(SearchErrorCode.SEARCH_CONFIGURATION_INVALID);
        }
        return new SearchException(SearchErrorCode.SEARCH_PROVIDER_FAILED);
    }

    private String errorStatus(String body) {
        if (body == null || body.isBlank()) {
            return "-";
        }
        try {
            JsonNode error = objectMapper.readTree(body)
                    .get("error");
            if (error != null && error.hasNonNull("status")) {
                return error.get("status")
                        .asString();
            }
        } catch (RuntimeException ignored) {
            // 오류 본문이 JSON이 아니면 상태 코드만 남긴다.
        }
        return "-";
    }

    private SearchException failed(Throwable cause) {
        return new SearchException(
                SearchErrorCode.SEARCH_PROVIDER_FAILED,
                cause
        );
    }
}
