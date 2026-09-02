package com.knot.backend.search.infrastructure;

import com.knot.backend.chat.infrastructure.LlmProperties;
import com.knot.backend.search.application.DocumentEmbeddingClient;
import com.knot.backend.search.application.EmbeddingProperties;
import com.knot.backend.search.domain.SearchErrorCode;
import com.knot.backend.search.domain.SearchException;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

final class OpenAiCompatibleEmbeddingClient implements DocumentEmbeddingClient {
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final LlmProperties llmProperties;
    private final EmbeddingProperties embeddingProperties;

    OpenAiCompatibleEmbeddingClient(
            HttpClient httpClient,
            ObjectMapper objectMapper,
            LlmProperties llmProperties,
            EmbeddingProperties embeddingProperties
    ) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.llmProperties = llmProperties;
        this.embeddingProperties = embeddingProperties;
    }

    @Override
    public List<double[]> embed(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }
        llmProperties.validate();
        embeddingProperties.validate();
        HttpResponse<String> response;
        try {
            response = httpClient.send(
                    request(texts),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );
        } catch (InterruptedException exception) {
            Thread.currentThread()
                    .interrupt();
            throw failed(exception);
        } catch (IOException | JacksonException exception) {
            throw failed(exception);
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new SearchException(SearchErrorCode.SEARCH_PROVIDER_FAILED);
        }
        try {
            return parseEmbeddings(
                    response.body(),
                    texts.size()
            );
        } catch (SearchException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw failed(exception);
        }
    }

    private HttpRequest request(List<String> texts) throws JacksonException {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put(
                "model",
                embeddingProperties.model()
        );
        payload.put(
                "input",
                texts
        );
        return HttpRequest.newBuilder(embeddingsUri())
                .timeout(llmProperties.requestTimeout())
                .header(
                        "Authorization",
                        "Bearer " + llmProperties.apiKey()
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

    private URI embeddingsUri() {
        String value = llmProperties.baseUri()
                .toString();
        String separator = value.endsWith("/") ? "" : "/";
        return URI.create(value + separator + "embeddings");
    }

    private List<double[]> parseEmbeddings(
            String body,
            int expectedSize
    ) throws JacksonException {
        JsonNode data = objectMapper.readTree(body)
                .get("data");
        if (data == null || !data.isArray() || data.size() != expectedSize) {
            throw new SearchException(SearchErrorCode.SEARCH_PROVIDER_FAILED);
        }
        List<double[]> embeddings = new java.util.ArrayList<>();
        for (JsonNode item : data) {
            JsonNode values = item.get("embedding");
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
            embeddings.add(embedding);
        }
        return List.copyOf(embeddings);
    }

    private SearchException failed(Throwable cause) {
        return new SearchException(
                SearchErrorCode.SEARCH_PROVIDER_FAILED,
                cause
        );
    }
}
