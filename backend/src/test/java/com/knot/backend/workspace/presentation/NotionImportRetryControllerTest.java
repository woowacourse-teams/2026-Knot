package com.knot.backend.workspace.presentation;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.knot.backend.auth.domain.AuthenticatedMember;
import com.knot.backend.global.exception.GlobalExceptionHandler;
import com.knot.backend.workspace.application.ContentImportCommandService;
import com.knot.backend.workspace.application.dto.result.ContentImportRunRequestResult;
import com.knot.backend.workspace.domain.ContentImportErrorCode;
import com.knot.backend.workspace.domain.ContentImportException;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class NotionImportRetryControllerTest {
    private static final Long ORIGINAL_IMPORT_RUN_ID = 1L;
    private static final long MEMBER_ID = 2L;
    private static final long NEW_IMPORT_RUN_ID = 3L;

    private ContentImportCommandService importCommandService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        importCommandService = mock(ContentImportCommandService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new NotionImportRetryController(importCommandService))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        SecurityContextHolder.getContext()
                .setAuthentication(memberAuthentication());
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @DisplayName("새 PENDING Run을 만들면 202와 새 상태 조회 Location을 반환한다")
    @Test
    void retry_success_created() throws Exception {
        // given
        when(
                importCommandService.retry(
                        ORIGINAL_IMPORT_RUN_ID,
                        MEMBER_ID
                )
        ).thenReturn(
                new ContentImportRunRequestResult(
                        NEW_IMPORT_RUN_ID,
                        true
                )
        );

        // when
        ResultActions result = mockMvc.perform(
                post(
                        "/api/v1/imports/{importRunId}/retry",
                        ORIGINAL_IMPORT_RUN_ID
                )
        );

        // then
        result.andExpect(status().isAccepted())
                .andExpect(
                        header().string(
                                HttpHeaders.LOCATION,
                                "/api/v1/imports/3"
                        )
                )
                .andExpect(jsonPath("$.id").value(NEW_IMPORT_RUN_ID));
        verify(importCommandService).retry(
                ORIGINAL_IMPORT_RUN_ID,
                MEMBER_ID
        );
    }

    @DisplayName("활성 Run이 있으면 같은 ID와 Location을 포함한 409를 반환한다")
    @Test
    void retry_success_existingActiveRun() throws Exception {
        // given
        when(
                importCommandService.retry(
                        ORIGINAL_IMPORT_RUN_ID,
                        MEMBER_ID
                )
        ).thenReturn(
                new ContentImportRunRequestResult(
                        NEW_IMPORT_RUN_ID,
                        false
                )
        );

        // when
        ResultActions result = mockMvc.perform(
                post(
                        "/api/v1/imports/{importRunId}/retry",
                        ORIGINAL_IMPORT_RUN_ID
                )
        );

        // then
        result.andExpect(status().isConflict())
                .andExpect(
                        header().string(
                                HttpHeaders.LOCATION,
                                "/api/v1/imports/3"
                        )
                )
                .andExpect(jsonPath("$.id").value(NEW_IMPORT_RUN_ID));
    }

    @DisplayName("중립 Content Import 오류를 기존 Notion 오류 계약으로 변환한다")
    @MethodSource("contentImportErrorMappings")
    @ParameterizedTest(name = "{0} -> {1}")
    void retry_failure_mapsContentImportError(
            ContentImportErrorCode contentImportErrorCode,
            String legacyCode,
            String legacyMessage,
            HttpStatus expectedStatus
    ) throws Exception {
        // given
        when(
                importCommandService.retry(
                        ORIGINAL_IMPORT_RUN_ID,
                        MEMBER_ID
                )
        ).thenThrow(new ContentImportException(contentImportErrorCode));

        // when
        ResultActions result = mockMvc.perform(
                post(
                        "/api/v1/imports/{importRunId}/retry",
                        ORIGINAL_IMPORT_RUN_ID
                )
        );

        // then
        result.andExpect(status().is(expectedStatus.value()))
                .andExpect(jsonPath("$.code").value(legacyCode))
                .andExpect(jsonPath("$.message").value(legacyMessage));
    }

    private static Stream<Arguments> contentImportErrorMappings() {
        return Stream.of(
                Arguments.of(
                        ContentImportErrorCode.INVALID_CONTENT_IMPORT_RUN_ID,
                        "INVALID_NOTION_IMPORT_RUN_ID",
                        "Notion Import 실행 ID가 올바르지 않습니다",
                        HttpStatus.BAD_REQUEST
                ),
                Arguments.of(
                        ContentImportErrorCode.INVALID_CONTENT_IMPORT_RUN,
                        "INVALID_NOTION_IMPORT_RUN",
                        "Notion Import 실행 정보가 올바르지 않습니다",
                        HttpStatus.BAD_REQUEST
                ),
                Arguments.of(
                        ContentImportErrorCode.CONTENT_IMPORT_RUN_NOT_FOUND,
                        "NOTION_IMPORT_RUN_NOT_FOUND",
                        "Notion Import 실행을 찾을 수 없습니다",
                        HttpStatus.NOT_FOUND
                ),
                Arguments.of(
                        ContentImportErrorCode.CONTENT_IMPORT_NOT_RETRYABLE,
                        "NOTION_IMPORT_NOT_RETRYABLE",
                        "실패한 Notion Import만 재시도할 수 있습니다",
                        HttpStatus.CONFLICT
                ),
                Arguments.of(
                        ContentImportErrorCode.CONTENT_SOURCE_CONNECTION_NOT_CONNECTED,
                        "NOTION_CONNECTION_NOT_CONNECTED",
                        "Notion 연결이 필요합니다",
                        HttpStatus.CONFLICT
                ),
                Arguments.of(
                        ContentImportErrorCode.CONTENT_SOURCE_CONNECTION_REAUTHENTICATION_REQUIRED,
                        "NOTION_CONNECTION_REAUTHENTICATION_REQUIRED",
                        "Notion 연결 재인증이 필요합니다",
                        HttpStatus.CONFLICT
                )
        );
    }

    private UsernamePasswordAuthenticationToken memberAuthentication() {
        return new UsernamePasswordAuthenticationToken(
                AuthenticatedMember.of(
                        MEMBER_ID,
                        "hyunsung",
                        null
                ),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
    }
}
