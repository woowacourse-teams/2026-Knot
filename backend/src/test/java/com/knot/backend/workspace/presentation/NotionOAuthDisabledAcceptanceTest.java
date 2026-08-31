package com.knot.backend.workspace.presentation;

import static org.assertj.core.api.Assertions.assertThat;

import com.knot.backend.testsupport.TestApplicationProperties;
import com.knot.backend.testsupport.TestcontainersConfiguration;
import com.knot.backend.workspace.application.NotionConnectionQueryService;
import com.knot.backend.workspace.application.NotionConnectionService;
import com.knot.backend.workspace.application.NotionOAuthAuthorizationService;
import com.knot.backend.workspace.application.NotionOAuthCallbackService;
import com.knot.backend.workspace.application.NotionOAuthClient;
import com.knot.backend.workspace.application.NotionOAuthSettings;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestConstructor;

@Tag("acceptance")
@Import(TestcontainersConfiguration.class)
@TestApplicationProperties
@SpringBootTest
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class NotionOAuthDisabledAcceptanceTest {
    private final ApplicationContext applicationContext;

    NotionOAuthDisabledAcceptanceTest(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @DisplayName("Notion OAuth가 비활성화되면 비밀값 없이 기동하고 관련 Bean을 등록하지 않는다")
    @Test
    void contextLoads_success_withoutNotionSecrets() {
        // given

        // when

        // then
        assertThat(applicationContext.getBeansOfType(NotionOAuthController.class)).isEmpty();
        assertThat(applicationContext.getBeansOfType(NotionOpenApiConfig.class)).isEmpty();
        assertThat(applicationContext.getBeansOfType(NotionConnectionQueryService.class)).isEmpty();
        assertThat(applicationContext.getBeansOfType(NotionConnectionService.class)).isEmpty();
        assertThat(applicationContext.getBeansOfType(NotionOAuthAuthorizationService.class)).isEmpty();
        assertThat(applicationContext.getBeansOfType(NotionOAuthCallbackService.class)).isEmpty();
        assertThat(applicationContext.getBeansOfType(NotionOAuthSettings.class)).isEmpty();
        assertThat(applicationContext.getBeansOfType(NotionOAuthClient.class)).isEmpty();
    }
}
