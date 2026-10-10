package com.knot.backend.document.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class DocumentTextTest {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\r\n", "\u00a0", "\u2007", "\u202f", "　", "\u001c"})
    @DisplayName("Java 공백과 Unicode 구분자만 있는 텍스트를 공백으로 판정한다")
    void isBlank_success_whitespace(String value) {
        // when & then
        assertThat(DocumentText.isBlank(value)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"문장", "　문장\u00a0", "\u200b", "\ufeff"})
    @DisplayName("문자와 공백으로 정의되지 않은 문자를 지우거나 공백으로 추정하지 않는다")
    void isBlank_success_nonWhitespace(String value) {
        // when & then
        assertThat(DocumentText.isBlank(value)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"　알림\u00a0  문구　", "알림\n\t문구", "알림 문구"})
    @DisplayName("연속 공백을 한 칸으로 바꾸고 앞뒤 공백을 제거한다")
    void normalizeWhitespace_success(String value) {
        // when & then
        assertThat(DocumentText.normalizeWhitespace(value)).isEqualTo("알림 문구");
    }
}
