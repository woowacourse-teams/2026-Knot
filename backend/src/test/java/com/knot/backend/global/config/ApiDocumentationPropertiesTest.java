package com.knot.backend.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ApiDocumentationPropertiesTest {
    @Test
    @DisplayName("API 문서 공개 설정의 기본값은 비활성화다")
    void default_success_disabled() {
        // given
        ApiDocumentationProperties properties = new ApiDocumentationProperties();

        // when
        boolean enabled = properties.isEnabled();

        // then
        assertThat(enabled).isFalse();
    }
}
