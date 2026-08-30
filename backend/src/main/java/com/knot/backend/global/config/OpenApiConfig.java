package com.knot.backend.global.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    public static final String ACCESS_TOKEN_COOKIE = "accessTokenCookie";

    @Bean
    public OpenAPI knotOpenAPI() {
        return new OpenAPI().info(
                new Info().title("Knot Backend API")
                        .version("1.0.0")
        )
                .components(securityComponents())
                .paths(oAuthPaths());
    }

    private Components securityComponents() {
        return new Components().addSecuritySchemes(
                ACCESS_TOKEN_COOKIE,
                new SecurityScheme().type(SecurityScheme.Type.APIKEY)
                        .in(SecurityScheme.In.COOKIE)
                        .name("__Host-KNOT_ACCESS_TOKEN")
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
