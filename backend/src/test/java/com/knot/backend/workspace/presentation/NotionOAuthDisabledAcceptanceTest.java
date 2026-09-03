package com.knot.backend.workspace.presentation;

import static org.assertj.core.api.Assertions.assertThat;

import com.knot.backend.testsupport.TestApplicationProperties;
import com.knot.backend.testsupport.TestcontainersConfiguration;
import com.knot.backend.workspace.application.ContentSourceConnectionQueryService;
import com.knot.backend.workspace.application.ContentSourceConnectionService;
import com.knot.backend.workspace.application.ContentSourceAuthorizationService;
import com.knot.backend.workspace.application.ContentSourceCallbackService;
import com.knot.backend.workspace.application.ContentSourceAuthorizationClient;
import com.knot.backend.workspace.application.ContentSourceAuthorizationSettings;
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
        assertThat(applicationContext.getBeansOfType(ContentSourceConnectionQueryService.class)).isEmpty();
        assertThat(applicationContext.getBeansOfType(ContentSourceConnectionService.class)).isEmpty();
        assertThat(applicationContext.getBeansOfType(ContentSourceAuthorizationService.class)).isEmpty();
        assertThat(applicationContext.getBeansOfType(ContentSourceCallbackService.class)).isEmpty();
        assertThat(applicationContext.getBeansOfType(ContentSourceAuthorizationSettings.class)).isEmpty();
        assertThat(applicationContext.getBeansOfType(ContentSourceAuthorizationClient.class)).isEmpty();
    }
}
