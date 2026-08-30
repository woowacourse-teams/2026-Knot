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
    public static final String ACCESS_TOKEN_COOKIE = "accessTokenCookie";
    public static final String CSRF_TOKEN_HEADER = "csrfTokenHeader";
    private static final String WORKSPACE_CONTROLLER = "com.knot.backend.workspace.presentation.WorkspaceController";

    @Bean
    public OpenAPI knotOpenAPI() {
        return new OpenAPI().info(
                new Info().title("Knot Backend API")
                        .version("1.0.0")
        )
                .components(securityComponents())
                .paths(oAuthPaths());
    }

    @Bean
    public OperationCustomizer workspaceSecurityOperationCustomizer() {
        return (
                operation,
                handlerMethod
        ) -> {
            if (isWorkspaceCreateOperation(handlerMethod)) {
                operation.summary("워크스페이스 생성")
                        .responses(workspaceCreateResponses());
                operation.security(
                        List.of(
                                new SecurityRequirement().addList(ACCESS_TOKEN_COOKIE)
                                        .addList(CSRF_TOKEN_HEADER)
                        )
                );
            }
            return operation;
        };
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
                                "CSRF 토큰 누락 또는 권한 없음",
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

    private Components securityComponents() {
        return new Components().addSecuritySchemes(
                ACCESS_TOKEN_COOKIE,
                new SecurityScheme().type(SecurityScheme.Type.APIKEY)
                        .in(SecurityScheme.In.COOKIE)
                        .name("__Host-KNOT_ACCESS_TOKEN")
        )
                .addSecuritySchemes(
                        CSRF_TOKEN_HEADER,
                        new SecurityScheme().type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("X-XSRF-TOKEN")
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

    private Paths oAuthPaths() {
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
