package com.knot.backend.global.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    @Bean
    public OpenAPI knotOpenAPI() {
        return new OpenAPI().info(
                new Info().title("Knot Backend API")
                        .version("1.0.0")
        )
                .paths(oAuthPaths());
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
