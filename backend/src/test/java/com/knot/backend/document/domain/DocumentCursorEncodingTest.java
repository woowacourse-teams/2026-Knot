package com.knot.backend.document.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DocumentCursorEncodingTest {
    private static final Instant TIME = Instant.parse("2026-10-06T00:00:00.123456Z");

    @Test
    @DisplayName("필터가 있는 커서는 조회 범위와 경계를 URL에 전달할 수 있게 인코딩한다")
    void encode_success_filtered() {
        String encoded = DocumentCursor.of(
                1,
                2,
                MyConfirmationState.PENDING,
                42L,
                TIME,
                301
        )
                .encode();

        assertThat(encoded).matches("[A-Za-z0-9_-]+");
        assertThat(
                new String(
                        Base64.getUrlDecoder()
                                .decode(encoded),
                        StandardCharsets.UTF_8
                )
        ).isEqualTo("1|1|2|PENDING|42|2026-10-06T00:00:00.123456Z|301");
    }

    @Test
    @DisplayName("필터 없는 커서는 두 조건이 생략된 것을 구분해 인코딩한다")
    void encode_success_unfiltered() {
        String encoded = DocumentCursor.of(
                1,
                2,
                null,
                null,
                TIME,
                301
        )
                .encode();

        assertThat(
                new String(
                        Base64.getUrlDecoder()
                                .decode(encoded),
                        StandardCharsets.UTF_8
                )
        ).isEqualTo("1|1|2|ALL|ALL|2026-10-06T00:00:00.123456Z|301");
    }
}
