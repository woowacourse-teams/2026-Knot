package com.knot.backend.notion.presentation;

import com.knot.backend.global.config.OpenApiConfig;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import java.util.List;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;

@Configuration(proxyBeanMethods = false)
public class NotionOpenApiConfig {
    private static final String CONTROLLER = "com.knot.backend.notion.presentation.NotionOAuthController";
    private static final String AUTHORIZATION_RESPONSE_SCHEMA = "NotionOAuthAuthorizationResponse";
    private static final String STATUS_RESPONSE_SCHEMA = "NotionConnectionStatusResponse";
    private static final String ERROR_RESPONSE_SCHEMA = "ErrorResponse";

    @Bean
    public OperationCustomizer notionOperationCustomizer() {
        return (
                operation,
                handlerMethod
        ) -> {
            if (!CONTROLLER.equals(
                    handlerMethod.getBeanType()
                            .getName()
            )) {
                return operation;
            }
            switch (handlerMethod.getMethod()
                    .getName()) {
                case "start" -> customizeStart(operation);
                case "callback" -> customizeCallback(operation);
                case "status" -> customizeStatus(operation);
                default -> {
                }
            }
            return operation;
        };
    }

    private void customizeStart(Operation operation) {
        operation.summary("Notion OAuth 연결 시작")
                .responses(
                        new ApiResponses().addApiResponse(
                                "201",
                                jsonResponse(
                                        "Notion authorization URL 생성",
                                        AUTHORIZATION_RESPONSE_SCHEMA
                                ).addHeaderObject(
                                        HttpHeaders.CACHE_CONTROL,
                                        noStoreHeader()
                                )
                        )
                                .addApiResponse(
                                        "400",
                                        jsonResponse(
                                                "잘못된 Workspace ID",
                                                ERROR_RESPONSE_SCHEMA
                                        )
                                )
                                .addApiResponse(
                                        "401",
                                        jsonResponse(
                                                "인증 필요",
                                                ERROR_RESPONSE_SCHEMA
                                        )
                                )
                                .addApiResponse(
                                        "403",
                                        jsonResponse(
                                                "CSRF 검증 실패 또는 OWNER 권한 없음",
                                                ERROR_RESPONSE_SCHEMA
                                        )
                                )
                                .addApiResponse(
                                        "404",
                                        jsonResponse(
                                                "워크스페이스 없음",
                                                ERROR_RESPONSE_SCHEMA
                                        )
                                )
                                .addApiResponse(
                                        "500",
                                        jsonResponse(
                                                "Notion OAuth 설정 오류",
                                                ERROR_RESPONSE_SCHEMA
                                        )
                                )
                )
                .security(authenticatedWithCsrf())
                .addParametersItem(csrfTokenParameter());
    }

    private void customizeCallback(Operation operation) {
        operation.summary("Notion OAuth callback 처리")
                .responses(
                        new ApiResponses().addApiResponse(
                                "303",
                                new ApiResponse().description("연결 결과 화면으로 redirect")
                                        .addHeaderObject(
                                                HttpHeaders.LOCATION,
                                                new Header().description("고정된 연결 성공 또는 실패 화면")
                                        )
                                        .addHeaderObject(
                                                HttpHeaders.CACHE_CONTROL,
                                                noStoreHeader()
                                        )
                        )
                                .addApiResponse(
                                        "500",
                                        jsonResponse(
                                                "예기치 않은 callback 처리 오류",
                                                ERROR_RESPONSE_SCHEMA
                                        )
                                )
                )
                .security(List.of());
    }

    private void customizeStatus(Operation operation) {
        operation.summary("Notion 연결 상태 조회")
                .responses(
                        new ApiResponses().addApiResponse(
                                "200",
                                jsonResponse(
                                        "Notion 연결 상태 조회",
                                        STATUS_RESPONSE_SCHEMA
                                )
                        )
                                .addApiResponse(
                                        "400",
                                        jsonResponse(
                                                "잘못된 Workspace ID",
                                                ERROR_RESPONSE_SCHEMA
                                        )
                                )
                                .addApiResponse(
                                        "401",
                                        jsonResponse(
                                                "인증 필요",
                                                ERROR_RESPONSE_SCHEMA
                                        )
                                )
                                .addApiResponse(
                                        "403",
                                        jsonResponse(
                                                "워크스페이스 접근 권한 없음",
                                                ERROR_RESPONSE_SCHEMA
                                        )
                                )
                                .addApiResponse(
                                        "404",
                                        jsonResponse(
                                                "워크스페이스 없음",
                                                ERROR_RESPONSE_SCHEMA
                                        )
                                )
                )
                .security(List.of(new SecurityRequirement().addList(OpenApiConfig.ACCESS_TOKEN_COOKIE)));
    }

    private List<SecurityRequirement> authenticatedWithCsrf() {
        return List.of(
                new SecurityRequirement().addList(OpenApiConfig.ACCESS_TOKEN_COOKIE)
                        .addList(OpenApiConfig.CSRF_TOKEN_HEADER)
        );
    }

    private Parameter csrfTokenParameter() {
        return new Parameter().name(OpenApiConfig.CSRF_TOKEN_HEADER_NAME)
                .in("header")
                .required(true)
                .description("CSRF 방지 토큰")
                .schema(new StringSchema());
    }

    private ApiResponse jsonResponse(
            String description,
            String schemaName
    ) {
        return new ApiResponse().description(description)
                .content(
                        new Content().addMediaType(
                                org.springframework.http.MediaType.APPLICATION_JSON_VALUE,
                                new MediaType().schema(new Schema<>().$ref("#/components/schemas/" + schemaName))
                        )
                );
    }

    private Header noStoreHeader() {
        return new Header().description("OAuth 임시값 캐시 방지: no-store");
    }
}
