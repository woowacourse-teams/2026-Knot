package com.knot.backend.workspace.presentation;

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
import org.springframework.web.method.HandlerMethod;

@Configuration(proxyBeanMethods = false)
public class WorkspaceInvitationOpenApiConfig {
    private static final String INVITATION_RESPONSE_SCHEMA = "WorkspaceInvitationResponse";
    private static final String ERROR_RESPONSE_SCHEMA = "ErrorResponse";

    @Bean
    public OperationCustomizer workspaceInvitationOperationCustomizer() {
        return (
                operation,
                handlerMethod
        ) -> {
            if (!isWorkspaceInvitationOperation(handlerMethod)) {
                return operation;
            }
            switch (handlerMethod.getMethod()
                    .getName()) {
                case "issue" -> customizeIssueOperation(operation);
                case "get" -> customizeGetOperation(operation);
                case "reissue" -> customizeReissueOperation(operation);
                default -> {
                }
            }
            return operation;
        };
    }

    private void customizeIssueOperation(Operation operation) {
        operation.summary("워크스페이스 초대 발급")
                .responses(issueResponses())
                .security(accessTokenSecurity())
                .addParametersItem(csrfTokenParameter());
    }

    private void customizeGetOperation(Operation operation) {
        operation.summary("워크스페이스 초대 조회")
                .responses(getResponses())
                .security(accessTokenSecurity());
    }

    private void customizeReissueOperation(Operation operation) {
        operation.summary("워크스페이스 초대 재발급")
                .responses(reissueResponses())
                .security(accessTokenSecurity())
                .addParametersItem(csrfTokenParameter());
    }

    private ApiResponses issueResponses() {
        return new ApiResponses().addApiResponse(
                "200",
                jsonResponse(
                        "기존 활성 초대 반환",
                        INVITATION_RESPONSE_SCHEMA
                )
        )
                .addApiResponse(
                        "201",
                        createdResponse(INVITATION_RESPONSE_SCHEMA)
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
                                "CSRF 검증 실패 또는 워크스페이스 접근 거부",
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
                                "활성 초대 복구 실패",
                                ERROR_RESPONSE_SCHEMA
                        )
                );
    }

    private ApiResponses getResponses() {
        return new ApiResponses().addApiResponse(
                "200",
                jsonResponse(
                        "활성 초대 조회",
                        INVITATION_RESPONSE_SCHEMA
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
                                "워크스페이스 접근 거부",
                                ERROR_RESPONSE_SCHEMA
                        )
                )
                .addApiResponse(
                        "404",
                        jsonResponse(
                                "워크스페이스 또는 활성 초대 없음",
                                ERROR_RESPONSE_SCHEMA
                        )
                )
                .addApiResponse(
                        "500",
                        jsonResponse(
                                "활성 초대 복구 실패",
                                ERROR_RESPONSE_SCHEMA
                        )
                );
    }

    private ApiResponses reissueResponses() {
        return new ApiResponses().addApiResponse(
                "201",
                createdResponse(INVITATION_RESPONSE_SCHEMA)
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
                                "CSRF 검증 실패 또는 워크스페이스 접근 거부",
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
                                "초대 생성 실패",
                                ERROR_RESPONSE_SCHEMA
                        )
                );
    }

    private ApiResponse createdResponse(String schemaName) {
        return jsonResponse(
                "새 초대 생성",
                schemaName
        ).addHeaderObject(
                HttpHeaders.LOCATION,
                new Header().description("생성된 초대 조회 URI")
                        .schema(new StringSchema().format("uri"))
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

    private Parameter csrfTokenParameter() {
        return new Parameter().name(OpenApiConfig.CSRF_TOKEN_HEADER_NAME)
                .description("CSRF 토큰")
                .in("header")
                .required(true)
                .schema(new StringSchema());
    }

    private List<SecurityRequirement> accessTokenSecurity() {
        return List.of(new SecurityRequirement().addList(OpenApiConfig.ACCESS_TOKEN_COOKIE));
    }

    private boolean isWorkspaceInvitationOperation(HandlerMethod handlerMethod) {
        return WorkspaceInvitationController.class.equals(handlerMethod.getBeanType());
    }
}
