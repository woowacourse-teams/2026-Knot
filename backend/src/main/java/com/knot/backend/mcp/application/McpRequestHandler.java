package com.knot.backend.mcp.application;

import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 원격 MCP 서버의 JSON-RPC 처리(기획서 6.6, 로드맵 A13·Q53). Streamable HTTP의 단일 엔드포인트에 POST된 메시지 하나를
 * 받아 {@code initialize}·{@code ping}·{@code tools/list}·{@code tools/call}을 처리한다. 세션({@code Mcp-Session-Id})을
 * 만들지 않는 무상태 서버이며, 알림과 클라이언트 응답은 202로 받기만 한다. 서버 발신 알림·SSE 스트림은 없다.
 *
 * 인증은 이 클래스 밖(기존 {@code JwtAuthenticationFilter})에서 끝나 있고, 여기서는 회원 ID만 받는다.
 */
@Component
@RequiredArgsConstructor
public class McpRequestHandler {
    public static final String SERVER_NAME = "knot";
    public static final String SERVER_TITLE = "Knot";
    public static final String SERVER_VERSION = "0.1.0";

    private static final String METHOD_INITIALIZE = "initialize";
    private static final String METHOD_PING = "ping";
    private static final String METHOD_TOOLS_LIST = "tools/list";
    private static final String METHOD_TOOLS_CALL = "tools/call";

    private final ObjectMapper objectMapper;
    private final McpToolExecutor toolExecutor;

    public McpHttpResponse handle(
            String body,
            long memberId
    ) {
        JsonNode message = parse(body);
        if (message == null) {
            return McpHttpResponse.badRequest(
                    JsonRpc.failure(
                            null,
                            JsonRpc.PARSE_ERROR,
                            "Parse error"
                    )
            );
        }
        if (message.isArray()) {
            // 2025-06-18 개정판부터 배치가 빠졌고 세 CLI 모두 메시지를 하나씩 보낸다.
            return McpHttpResponse.badRequest(
                    JsonRpc.failure(
                            null,
                            JsonRpc.INVALID_REQUEST,
                            "Batch requests are not supported"
                    )
            );
        }
        if (!message.isObject() || !isJsonRpcVersion(message)) {
            return McpHttpResponse.badRequest(
                    JsonRpc.failure(
                            null,
                            JsonRpc.INVALID_REQUEST,
                            "Invalid Request"
                    )
            );
        }
        JsonNode method = message.get("method");
        if (method == null || !method.isString()) {
            if (message.has("result") || message.has("error")) {
                // 클라이언트가 서버 요청에 답한 것. 서버가 요청을 보내지 않으므로 받기만 한다.
                return McpHttpResponse.accepted();
            }
            return McpHttpResponse.badRequest(
                    JsonRpc.failure(
                            null,
                            JsonRpc.INVALID_REQUEST,
                            "Invalid Request"
                    )
            );
        }
        JsonNode id = message.get("id");
        if (id == null || id.isNull()) {
            // 알림(notifications/initialized·notifications/cancelled 등)은 처리할 것이 없다.
            return McpHttpResponse.accepted();
        }
        if (!id.isString() && !id.isNumber()) {
            return McpHttpResponse.badRequest(
                    JsonRpc.failure(
                            null,
                            JsonRpc.INVALID_REQUEST,
                            "Invalid Request"
                    )
            );
        }
        return McpHttpResponse.ok(
                dispatch(
                        id,
                        method.asString(),
                        message.get("params"),
                        memberId
                )
        );
    }

    private Object dispatch(
            JsonNode id,
            String method,
            JsonNode params,
            long memberId
    ) {
        return switch (method) {
            case METHOD_INITIALIZE -> JsonRpc.success(
                    id,
                    initialize(params)
            );
            case METHOD_PING -> JsonRpc.success(
                    id,
                    Map.of()
            );
            case METHOD_TOOLS_LIST -> JsonRpc.success(
                    id,
                    Map.of(
                            "tools",
                            McpToolCatalog.definitions()
                    )
            );
            case METHOD_TOOLS_CALL -> callTool(
                    id,
                    params,
                    memberId
            );
            default -> JsonRpc.failure(
                    id,
                    JsonRpc.METHOD_NOT_FOUND,
                    "Method not found: " + method
            );
        };
    }

    private Map<String, Object> initialize(JsonNode params) {
        JsonNode requested = params == null ? null : params.get("protocolVersion");
        String protocolVersion = McpProtocolVersions.negotiate(
                requested != null && requested.isString() ? requested.asString() : null
        );
        Map<String, Object> result = new LinkedHashMap<>();
        result.put(
                "protocolVersion",
                protocolVersion
        );
        result.put(
                "capabilities",
                Map.of(
                        "tools",
                        Map.of(
                                "listChanged",
                                false
                        )
                )
        );
        Map<String, Object> serverInfo = new LinkedHashMap<>();
        serverInfo.put(
                "name",
                SERVER_NAME
        );
        serverInfo.put(
                "title",
                SERVER_TITLE
        );
        serverInfo.put(
                "version",
                SERVER_VERSION
        );
        result.put(
                "serverInfo",
                serverInfo
        );
        result.put(
                "instructions",
                McpServerInstructions.TEXT
        );
        return result;
    }

    private Object callTool(
            JsonNode id,
            JsonNode params,
            long memberId
    ) {
        JsonNode name = params == null ? null : params.get("name");
        if (name == null || !name.isString()) {
            return JsonRpc.failure(
                    id,
                    JsonRpc.INVALID_PARAMS,
                    "Missing tool name"
            );
        }
        if (!McpToolCatalog.contains(name.asString())) {
            return JsonRpc.failure(
                    id,
                    JsonRpc.INVALID_PARAMS,
                    "Unknown tool: " + name.asString()
            );
        }
        try {
            McpToolResult result = toolExecutor.execute(
                    name.asString(),
                    params.get("arguments"),
                    memberId
            );
            return JsonRpc.success(
                    id,
                    result.toCallToolResult()
            );
        } catch (McpToolInputException exception) {
            return JsonRpc.failure(
                    id,
                    JsonRpc.INVALID_PARAMS,
                    exception.getMessage()
            );
        }
    }

    private JsonNode parse(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(body);
        } catch (JacksonException exception) {
            return null;
        }
    }

    private static boolean isJsonRpcVersion(JsonNode message) {
        JsonNode version = message.get("jsonrpc");
        return version != null && version.isString() && JsonRpc.VERSION.equals(version.asString());
    }
}
