package com.knot.backend.workspace.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.time.Instant;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ImportedPageTest {
    private static final Instant CREATED_AT = Instant.parse("2026-08-31T00:00:00Z");

    @DisplayName("자기 자신을 부모로 지정한 Page는 생성할 수 없다")
    @Test
    void create_failure_selfParent() {
        // given
        ThrowingCallable action = () -> ImportedPage.create(
                1L,
                2L,
                "page-id",
                "page-id",
                "title",
                "content",
                0,
                "https://example.com/page-id",
                CREATED_AT,
                CREATED_AT
        );

        // when
        Throwable thrown = catchThrowable(action);

        // then
        assertThat(thrown).isInstanceOf(ImportedPageException.class)
                .extracting(exception -> ((ImportedPageException) exception).getErrorCode())
                .isEqualTo(ImportedPageErrorCode.INVALID_IMPORTED_PAGE);
    }
}
