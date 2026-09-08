package com.knot.backend.search.infrastructure.gemini;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.knot.backend.search.domain.SearchErrorCode;
import com.knot.backend.search.domain.SearchException;
import java.net.URI;
import java.time.Duration;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GeminiEmbeddingPropertiesTest {

    @Test
    @DisplayName("batchEmbedContents URI와 model 리소스 이름을 설정값으로 만든다")
    void batchEmbedContentsUri_success_buildsUriAndResourceName() {
        // given
        GeminiEmbeddingProperties properties = new GeminiEmbeddingProperties(
                URI.create("https://generativelanguage.googleapis.com"),
                "gemini-secret",
                "gemini-embedding-001",
                Duration.ofSeconds(5),
                5,
                Duration.ofSeconds(5)
        );

        // when
        URI uri = properties.batchEmbedContentsUri();

        // then
        assertThat(uri).hasToString(
                "https://generativelanguage.googleapis.com/v1beta/models/gemini-embedding-001:batchEmbedContents"
        );
        assertThat(properties.modelResourceName()).isEqualTo("models/gemini-embedding-001");
    }

    @Test
    @DisplayName("모델 이름에 models/ 접두사가 있어도 같은 URI와 리소스 이름을 만든다")
    void batchEmbedContentsUri_success_acceptsPrefixedModel() {
        // given
        GeminiEmbeddingProperties properties = new GeminiEmbeddingProperties(
                URI.create("https://generativelanguage.googleapis.com/"),
                "gemini-secret",
                "models/gemini-embedding-001",
                Duration.ofSeconds(5),
                5,
                Duration.ofSeconds(5)
        );

        // when
        URI uri = properties.batchEmbedContentsUri();

        // then
        assertThat(uri).hasToString(
                "https://generativelanguage.googleapis.com/v1beta/models/gemini-embedding-001:batchEmbedContents"
        );
        assertThat(properties.modelResourceName()).isEqualTo("models/gemini-embedding-001");
    }

    @Test
    @DisplayName("API 키가 비어 있으면 검색 설정 오류로 실패한다")
    void validate_failure_blankApiKey() {
        // given
        GeminiEmbeddingProperties properties = new GeminiEmbeddingProperties(
                URI.create("https://generativelanguage.googleapis.com"),
                " ",
                "gemini-embedding-001",
                Duration.ofSeconds(5),
                5,
                Duration.ofSeconds(5)
        );

        // when
        ThrowingCallable action = properties::validate;

        // then
        assertThatThrownBy(action).isInstanceOfSatisfying(
                SearchException.class,
                exception -> assertThat(exception.searchErrorCode())
                        .isEqualTo(SearchErrorCode.SEARCH_CONFIGURATION_INVALID)
        );
    }

    @Test
    @DisplayName("base URI가 http(s) 절대 경로가 아니거나 timeout이 0이면 검색 설정 오류로 실패한다")
    void validate_failure_invalidUriOrTimeout() {
        // given
        GeminiEmbeddingProperties relativeUri = new GeminiEmbeddingProperties(
                URI.create("generativelanguage.googleapis.com"),
                "gemini-secret",
                "gemini-embedding-001",
                Duration.ofSeconds(5),
                5,
                Duration.ofSeconds(5)
        );
        GeminiEmbeddingProperties zeroTimeout = new GeminiEmbeddingProperties(
                URI.create("https://generativelanguage.googleapis.com"),
                "gemini-secret",
                "gemini-embedding-001",
                Duration.ZERO,
                5,
                Duration.ofSeconds(5)
        );
        GeminiEmbeddingProperties blankModel = new GeminiEmbeddingProperties(
                URI.create("https://generativelanguage.googleapis.com"),
                "gemini-secret",
                "models/",
                Duration.ofSeconds(5),
                5,
                Duration.ofSeconds(5)
        );

        // when & then
        for (GeminiEmbeddingProperties properties : java.util.List.of(
                relativeUri,
                zeroTimeout,
                blankModel
        )) {
            assertThatThrownBy(properties::validate).isInstanceOfSatisfying(
                    SearchException.class,
                    exception -> assertThat(exception.searchErrorCode())
                            .isEqualTo(SearchErrorCode.SEARCH_CONFIGURATION_INVALID)
            );
        }
    }

    @Test
    @DisplayName("재시도 횟수가 1 미만이거나 재시도 지연이 음수면 검색 설정 오류로 실패한다")
    void validate_failure_invalidRetrySettings() {
        // given
        GeminiEmbeddingProperties zeroAttempts = new GeminiEmbeddingProperties(
                URI.create("https://generativelanguage.googleapis.com"),
                "gemini-secret",
                "gemini-embedding-001",
                Duration.ofSeconds(5),
                0,
                Duration.ofSeconds(5)
        );
        GeminiEmbeddingProperties negativeDelay = new GeminiEmbeddingProperties(
                URI.create("https://generativelanguage.googleapis.com"),
                "gemini-secret",
                "gemini-embedding-001",
                Duration.ofSeconds(5),
                5,
                Duration.ofSeconds(-1)
        );
        GeminiEmbeddingProperties noRetry = new GeminiEmbeddingProperties(
                URI.create("https://generativelanguage.googleapis.com"),
                "gemini-secret",
                "gemini-embedding-001",
                Duration.ofSeconds(5),
                1,
                Duration.ZERO
        );

        // when & then
        for (GeminiEmbeddingProperties properties : java.util.List.of(
                zeroAttempts,
                negativeDelay
        )) {
            assertThatThrownBy(properties::validate).isInstanceOfSatisfying(
                    SearchException.class,
                    exception -> assertThat(exception.searchErrorCode())
                            .isEqualTo(SearchErrorCode.SEARCH_CONFIGURATION_INVALID)
            );
        }
        noRetry.validate();
    }
}
