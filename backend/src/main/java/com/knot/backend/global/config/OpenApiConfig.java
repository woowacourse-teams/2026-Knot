package com.knot.backend.global.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.util.List;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.HandlerMethod;

@Configuration
public class OpenApiConfig {
    /** `Authorization: Bearer <JWT>` 보안 스킴 이름. 인증 자격증명은 이것 하나뿐이다(기획서 5.1) */
    public static final String BEARER_AUTH = "bearerAuth";
    private static final String WORKSPACE_PACKAGE = "com.knot.backend.workspace.presentation.";
    private static final String WORKSPACE_CONTROLLER = WORKSPACE_PACKAGE + "WorkspaceController";
    private static final String WORKSPACE_QUERY_CONTROLLER = WORKSPACE_PACKAGE + "WorkspaceQueryController";

    @Bean
    public OpenAPI knotOpenAPI() {
        return new OpenAPI().info(
                new Info().title("Knot Backend API")
                        .version("1.0.0")
        )
                .components(securityComponents())
                .paths(applicationPaths());
    }

    @Bean
    public OperationCustomizer workspaceOperationCustomizer() {
        return (
                operation,
                handlerMethod
        ) -> {
            if (isWorkspaceCreateOperation(handlerMethod)) {
                customizeWorkspaceCreateOperation(operation);
            }
            if (isWorkspaceDetailOperation(handlerMethod)) {
                customizeWorkspaceDetailOperation(operation);
            }
            if (isWorkspaceListOperation(handlerMethod)) {
                customizeWorkspaceListOperation(operation);
            }
            return operation;
        };
    }

    private void customizeWorkspaceCreateOperation(Operation operation) {
        operation.summary("워크스페이스 생성")
                .responses(workspaceCreateResponses());
        operation.security(List.of(new SecurityRequirement().addList(BEARER_AUTH)));
    }

    private void customizeWorkspaceDetailOperation(Operation operation) {
        operation.summary("워크스페이스 단건 조회")
                .responses(workspaceDetailResponses());
        operation.security(List.of(new SecurityRequirement().addList(BEARER_AUTH)));
    }

    private void customizeWorkspaceListOperation(Operation operation) {
        operation.summary("내 워크스페이스 목록 조회")
                .responses(workspaceListResponses());
        operation.security(List.of(new SecurityRequirement().addList(BEARER_AUTH)));
    }

    private ApiResponses workspaceCreateResponses() {
        return new ApiResponses().addApiResponse(
                "201",
                jsonResponse(
                        "워크스페이스 생성 성공",
                        "WorkspaceCreateResponse"
                )
        )
                .addApiResponse(
                        "400",
                        jsonResponse(
                                "워크스페이스 이름 규칙 위반",
                                "ErrorResponse"
                        )
                )
                .addApiResponse(
                        "401",
                        jsonResponse(
                                "인증되지 않은 요청",
                                "ErrorResponse"
                        )
                )
                .addApiResponse(
                        "403",
                        jsonResponse(
                                "권한 없음",
                                "ErrorResponse"
                        )
                );
    }

    private ApiResponses workspaceDetailResponses() {
        return new ApiResponses().addApiResponse(
                "200",
                jsonResponse(
                        "워크스페이스 조회 성공",
                        "WorkspaceDetailResponse"
                )
        )
                .addApiResponse(
                        "400",
                        jsonResponse(
                                "워크스페이스 ID 형식 오류",
                                "ErrorResponse"
                        )
                )
                .addApiResponse(
                        "401",
                        jsonResponse(
                                "인증되지 않은 요청",
                                "ErrorResponse"
                        )
                )
                .addApiResponse(
                        "403",
                        jsonResponse(
                                "워크스페이스 접근 권한 없음",
                                "ErrorResponse"
                        )
                )
                .addApiResponse(
                        "404",
                        jsonResponse(
                                "워크스페이스를 찾을 수 없음",
                                "ErrorResponse"
                        )
                );
    }

    private ApiResponses workspaceListResponses() {
        return new ApiResponses().addApiResponse(
                "200",
                jsonResponse(
                        "워크스페이스 목록 조회 성공",
                        "WorkspaceListResponse"
                )
        )
                .addApiResponse(
                        "401",
                        jsonResponse(
                                "인증되지 않은 요청",
                                "ErrorResponse"
                        )
                );
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

    private boolean isWorkspaceCreateOperation(HandlerMethod handlerMethod) {
        return WORKSPACE_CONTROLLER.equals(
                handlerMethod.getBeanType()
                        .getName()
        ) && "create".equals(handlerMethod.getMethod()
                .getName()
        );
    }

    private boolean isWorkspaceDetailOperation(HandlerMethod handlerMethod) {
        return WORKSPACE_QUERY_CONTROLLER.equals(
                handlerMethod.getBeanType()
                        .getName()
        ) && "detail".equals(handlerMethod.getMethod()
                .getName()
        );
    }

    private boolean isWorkspaceListOperation(HandlerMethod handlerMethod) {
        return WORKSPACE_QUERY_CONTROLLER.equals(
                handlerMethod.getBeanType()
                        .getName()
        ) && "list".equals(handlerMethod.getMethod()
                .getName()
        );
    }

    private Components securityComponents() {
        return new Components().addSecuritySchemes(
                BEARER_AUTH,
                new SecurityScheme().type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
        )
                .addSchemas(
                        "ErrorResponse",
                        errorResponseSchema()
                )
                .addSchemas(
                        "FieldErrorResponse",
                        fieldErrorResponseSchema()
                );
    }

    private Schema<?> errorResponseSchema() {
        return new Schema<>().type("object")
                .addProperty(
                        "code",
                        new StringSchema()
                )
                .addProperty(
                        "message",
                        new StringSchema()
                )
                .addProperty(
                        "fieldErrors",
                        new ArraySchema().items(new Schema<>().$ref("#/components/schemas/FieldErrorResponse"))
                );
    }

    private Schema<?> fieldErrorResponseSchema() {
        return new Schema<>().type("object")
                .addProperty(
                        "field",
                        new StringSchema()
                )
                .addProperty(
                        "reason",
                        new StringSchema()
                );
    }

    private Paths applicationPaths() {
        return new Paths().addPathItem(
                "/oauth2/authorization/{registrationId}",
                new PathItem().get(
                        new Operation().operationId("startOAuthLogin")
                                .summary("OAuth 로그인 시작")
                                .addParametersItem(
                                        new Parameter().name("registrationId")
                                                .in("path")
                                                .required(true)
                                                .schema(new StringSchema())
                                )
                                .responses(
                                        new ApiResponses().addApiResponse(
                                                "302",
                                                new ApiResponse()
                                                        .description("OAuth provider authorization endpoint로 redirect")
                                                        .addHeaderObject(
                                                                "Location",
                                                                new Header()
                                                                        .description("OAuth provider authorization URL")
                                                        )
                                        )
                                )
                )
        );
    }
}
