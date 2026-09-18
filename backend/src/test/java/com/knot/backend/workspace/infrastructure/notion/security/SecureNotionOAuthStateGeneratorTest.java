package com.knot.backend.workspace.infrastructure.notion.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SecureNotionOAuthStateGeneratorTest {

    @DisplayName("OAuth state는 256-bit URL-safe 난수로 생성한다")
    @Test
    void generate_success_format() {
        // given
        SecureNotionOAuthStateGenerator generator = new SecureNotionOAuthStateGenerator();

        // when
        String state = generator.generate();

        // then
        assertThat(state).hasSize(43)
                .matches("[A-Za-z0-9_-]+");
    }

    @DisplayName("연속 생성한 OAuth state는 서로 다르다")
    @Test
    void generate_success_uniqueValues() {
        // given
        SecureNotionOAuthStateGenerator generator = new SecureNotionOAuthStateGenerator();
        String first = generator.generate();

        // when
        String second = generator.generate();

        // then
        assertThat(second).isNotEqualTo(first);
    }
}
