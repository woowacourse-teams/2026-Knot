package com.knot.backend.search.presentation;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.knot.backend.auth.domain.AuthenticatedMember;
import com.knot.backend.global.exception.GlobalExceptionHandler;
import com.knot.backend.search.application.SearchConversationListService;
import com.knot.backend.search.application.SearchMessageListService;
import com.knot.backend.search.application.dto.query.SearchMessageListParameters;
import com.knot.backend.search.application.dto.result.SearchMessageListResult;
import com.knot.backend.search.domain.SearchErrorCode;
import com.knot.backend.search.domain.SearchException;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class SearchMessageListControllerTest {
    private static final String PATH = "/api/v1/workspaces/1/search/conversations/3/messages";

    private SearchMessageListService service;
    private SearchConversationListService listService;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(SearchMessageListService.class);
        listService = mock(SearchConversationListService.class);
        mvc = MockMvcBuilders.standaloneSetup(
                new SearchConversationController(
                        listService,
                        service
                )
        )
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                AuthenticatedMember.of(
                                        2L,
                                        "조회자",
                                        null
                                ),
                                null,
                                List.of()
                        )
                );
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("인증 Member와 기본 페이지 크기를 메시지 서비스에 전달한다")
    void find_success_defaults() throws Exception {
        // given
        SearchMessageListParameters parameters = SearchMessageListParameters.of(
                null,
                null
        );
        when(
                service.find(
                        1,
                        2,
                        3,
                        parameters
                )
        ).thenReturn(
                new SearchMessageListResult(
                        3,
                        List.of(),
                        false,
                        null
                )
        );
        // when & then
        mvc.perform(get(PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversationId").value(3))
                .andExpect(jsonPath("$.items").isEmpty());
        verify(service).find(
                1,
                2,
                3,
                parameters
        );
        verifyNoInteractions(listService);
    }

    @Test
    @DisplayName("beforeSequence와 size를 정수로 변환하여 전달한다")
    void find_success_queryBinding() throws Exception {
        // given
        SearchMessageListParameters parameters = SearchMessageListParameters.of(
                10,
                1
        );
        when(
                service.find(
                        1,
                        2,
                        3,
                        parameters
                )
        ).thenReturn(
                new SearchMessageListResult(
                        3,
                        List.of(),
                        false,
                        null
                )
        );
        // when & then
        mvc.perform(
                get(PATH).param(
                        "beforeSequence",
                        "10"
                )
                        .param(
                                "size",
                                "1"
                        )
        )
                .andExpect(status().isOk());
        verify(service).find(
                1,
                2,
                3,
                parameters
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"text", "2147483648", "0"})
    @DisplayName("형식 또는 범위 오류는 서비스 호출 전 400이다")
    void find_failure_invalidParameter(String size) throws Exception {
        // when & then
        mvc.perform(
                get(PATH).param(
                        "size",
                        size
                )
        )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
        verifyNoInteractions(
                service,
                listService
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"SEARCH_ACCESS_DENIED", "CONVERSATION_NOT_FOUND"})
    @DisplayName("서비스의 접근 오류와 대화 없음 오류를 공통 JSON으로 변환한다")
    void find_failure_serviceError(String code) throws Exception {
        // given
        SearchErrorCode error = SearchErrorCode.valueOf(code);
        when(
                service.find(
                        1,
                        2,
                        3,
                        SearchMessageListParameters.of(
                                null,
                                null
                        )
                )
        ).thenThrow(new SearchException(error));
        int expectedStatus = 404;
        if (error == SearchErrorCode.SEARCH_ACCESS_DENIED) {
            expectedStatus = 403;
        }
        // when & then
        mvc.perform(get(PATH))
                .andExpect(status().is(expectedStatus))
                .andExpect(jsonPath("$.code").value(code));
    }
}
