package com.knot.backend.global.infrastructure.llm;

import com.knot.backend.global.config.LlmProperties;
import com.knot.backend.global.exception.LlmErrorCode;
import com.knot.backend.global.exception.LlmException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

public class LmStudioClient implements LlmClient {

    private static final String ENGINE_ERROR_PREFIX = "Engine protocol predict request returned 400: ";

    private final HttpClient httpClient;
    private final ObjectMapper mapper;
    private final LlmProperties properties;

    public LmStudioClient(
            HttpClient httpClient,
            ObjectMapper mapper,
            LlmProperties properties
    ) {
        this.httpClient = httpClient;
        this.mapper = mapper.rebuild()
                .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .build();
        this.properties = properties;
    }

    @Override
    public String complete(LlmCompletionRequest request) {
        validateRequest(request);
        ObjectNode body = createRequestBody(request);
        HttpResponse<String> response = awaitResponse(buildHttpRequest(body));
        validateCompletionStatus(response);
        return readAssistantContent(response.body());
    }

    private void validateRequest(LlmCompletionRequest request) {
        if (request == null || request.messages() == null || request.messages()
                .isEmpty()) {
            throw new LlmException(LlmErrorCode.LLM_INVALID_REQUEST);
        }
        if (request.schemaName() == null || request.schemaName()
                .isBlank()) {
            throw new LlmException(LlmErrorCode.LLM_INVALID_REQUEST);
        }
        if (request.outputSchema() == null || !request.outputSchema()
                .isObject()) {
            throw new LlmException(LlmErrorCode.LLM_INVALID_REQUEST);
        }
        validateMessages(request);
        validateOptions(request.options());
    }

    private void validateMessages(LlmCompletionRequest request) {
        for (LlmMessage message : request.messages()) {
            if (message.role() == null || message.role()
                    .isBlank() || message.content() == null) {
                throw new LlmException(LlmErrorCode.LLM_INVALID_REQUEST);
            }
        }
    }

    private void validateOptions(LlmGenerationOptions options) {
        if (options == null || options.maxTokens() <= 0 || options.topK() < 0) {
            throw new LlmException(LlmErrorCode.LLM_INVALID_REQUEST);
        }
        validateSampling(options);
        if (options.thinkingBudgetTokens() != null && options.thinkingBudgetTokens() < 0) {
            throw new LlmException(LlmErrorCode.LLM_INVALID_REQUEST);
        }
    }

    private void validateSampling(LlmGenerationOptions options) {
        if (!Double.isFinite(options.temperature()) || options.temperature() < 0 || options.temperature() > 2) {
            throw new LlmException(LlmErrorCode.LLM_INVALID_REQUEST);
        }
        if (!Double.isFinite(options.topP()) || options.topP() <= 0 || options.topP() > 1) {
            throw new LlmException(LlmErrorCode.LLM_INVALID_REQUEST);
        }
        if (!Double.isFinite(options.repeatPenalty()) || options.repeatPenalty() <= 0) {
            throw new LlmException(LlmErrorCode.LLM_INVALID_REQUEST);
        }
    }

    private ObjectNode createRequestBody(LlmCompletionRequest request) {
        LlmGenerationOptions options = request.options();
        ObjectNode body = mapper.createObjectNode();
        body.put(
                "model",
                properties.getModel()
        );
        body.put(
                "stream",
                false
        );
        body.put(
                "temperature",
                options.temperature()
        );
        body.put(
                "top_p",
                options.topP()
        );
        body.put(
                "top_k",
                options.topK()
        );
        body.put(
                "repeat_penalty",
                options.repeatPenalty()
        );
        body.put(
                "max_tokens",
                options.maxTokens()
        );
        ArrayNode messages = body.putArray("messages");
        for (LlmMessage message : request.messages()) {
            ObjectNode item = messages.addObject();
            item.put(
                    "role",
                    message.role()
            );
            item.put(
                    "content",
                    message.content()
            );
        }
        ObjectNode format = body.putObject("response_format");
        format.put(
                "type",
                "json_schema"
        );
        ObjectNode schema = format.putObject("json_schema");
        schema.put(
                "name",
                request.schemaName()
        );
        schema.put(
                "strict",
                true
        );
        schema.set(
                "schema",
                request.outputSchema()
                        .deepCopy()
        );
        addThinkingSettings(
                body,
                options
        );
        return body;
    }

    private void addThinkingSettings(
            ObjectNode body,
            LlmGenerationOptions options
    ) {
        String reasoning = "off";
        if (options.thinkingEnabled()) {
            reasoning = "on";
        }
        body.put(
                "reasoning",
                reasoning
        );
        ObjectNode kwargs = body.putObject("chat_template_kwargs");
        kwargs.put(
                "enable_thinking",
                options.thinkingEnabled()
        );
        if (options.thinkingBudgetTokens() != null) {
            body.put(
                    "reasoning_budget",
                    options.thinkingBudgetTokens()
            );
        }
    }

    private HttpRequest buildHttpRequest(ObjectNode body) {
        return HttpRequest.newBuilder(
                properties.getBaseUrl()
                        .resolve("/v1/chat/completions")
        )
                .timeout(properties.getRequestTimeout())
                .header(
                        "Authorization",
                        "Bearer " + properties.getApiToken()
                )
                .header(
                        "Content-Type",
                        "application/json"
                )
                .POST(
                        HttpRequest.BodyPublishers.ofString(
                                mapper.writeValueAsString(body),
                                StandardCharsets.UTF_8
                        )
                )
                .build();
    }

    private HttpResponse<String> awaitResponse(HttpRequest request) {
        CompletableFuture<HttpResponse<String>> future = httpClient.sendAsync(
                request,
                HttpResponse.BodyHandlers.limiting(
                        HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8),
                        properties.getMaxResponseBytes()
                )
        );
        try {
            return future.get(
                    properties.getRequestTimeout()
                            .toMillis(),
                    TimeUnit.MILLISECONDS
            );
        } catch (TimeoutException exception) {
            future.cancel(true);
            throw new LlmException(LlmErrorCode.LLM_TIMEOUT);
        } catch (InterruptedException exception) {
            future.cancel(true);
            Thread.currentThread()
                    .interrupt();
            throw new LlmException(LlmErrorCode.LLM_CALL_INTERRUPTED);
        } catch (ExecutionException exception) {
            if (HttpTimeoutException.class.isInstance(exception.getCause())) {
                throw new LlmException(LlmErrorCode.LLM_TIMEOUT);
            }
            throw new LlmException(LlmErrorCode.LLM_UNAVAILABLE);
        }
    }

    private void validateCompletionStatus(HttpResponse<String> response) {
        int status = response.statusCode();
        if (status == 200) {
            return;
        }
        if (status == 401 || status == 403) {
            throw new LlmException(LlmErrorCode.LLM_AUTHENTICATION_FAILED);
        }
        if (status == 429) {
            throw new LlmException(LlmErrorCode.LLM_RATE_LIMITED);
        }
        if (status >= 500) {
            throw new LlmException(LlmErrorCode.LLM_UNAVAILABLE);
        }
        if (status == 400 && isContextLimitResponse(response.body())) {
            throw new LlmException(LlmErrorCode.LLM_INPUT_LIMIT_EXCEEDED);
        }
        throw new LlmException(LlmErrorCode.LLM_REQUEST_REJECTED);
    }

    private boolean isContextLimitResponse(String body) {
        try {
            JsonNode root = mapper.readTree(body);
            if (root == null || !root.isObject()) {
                return false;
            }
            JsonNode error = root.path("error");
            if (error.isString() && error.asString()
                    .startsWith(ENGINE_ERROR_PREFIX)) {
                JsonNode embedded = mapper.readTree(
                        error.asString()
                                .substring(ENGINE_ERROR_PREFIX.length())
                );
                if (embedded == null) {
                    return false;
                }
                error = embedded.path("error");
            }
            return "exceed_context_size_error".equals(
                    error.path("type")
                            .asString()
            );
        } catch (JacksonException exception) {
            return false;
        }
    }

    private String readAssistantContent(String responseBody) {
        JsonNode root;
        try {
            root = mapper.readTree(responseBody);
        } catch (JacksonException exception) {
            throw new LlmException(LlmErrorCode.LLM_INVALID_RESPONSE);
        }
        if (root == null || !root.isObject()) {
            throw new LlmException(LlmErrorCode.LLM_INVALID_RESPONSE);
        }
        JsonNode choices = root.path("choices");
        if (!choices.isArray() || choices.size() != 1) {
            throw new LlmException(LlmErrorCode.LLM_INVALID_RESPONSE);
        }
        JsonNode choice = choices.get(0);
        validateFinishReason(choice);
        JsonNode content = choice.path("message")
                .path("content");
        if (!content.isString() || content.asString()
                .isBlank()) {
            throw new LlmException(LlmErrorCode.LLM_INVALID_RESPONSE);
        }
        return content.asString();
    }

    private void validateFinishReason(JsonNode choice) {
        JsonNode reason = choice.path("finish_reason");
        if ("length".equals(reason.asString())) {
            throw new LlmException(LlmErrorCode.LLM_OUTPUT_LIMIT_EXCEEDED);
        }
        if (!"stop".equals(reason.asString())) {
            throw new LlmException(LlmErrorCode.LLM_INVALID_RESPONSE);
        }
    }
}
