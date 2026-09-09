package com.knot.backend.chat.application;

import com.knot.backend.chat.application.dto.result.WorkspaceSearchResult;
import com.knot.backend.chat.domain.ChatErrorCode;
import com.knot.backend.chat.domain.ChatException;
import com.knot.backend.search.application.PublishedDocumentSearchService;
import com.knot.backend.search.application.SearchContext;
import com.knot.backend.search.domain.SearchErrorCode;
import com.knot.backend.search.domain.SearchException;
import com.knot.backend.workspace.application.WorkspaceQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 데스크톱 로컬 MCP 서버의 {@code search_documents} 도구가 부르는 Workspace 검색(기획서 6.4, 로드맵
 * S7). Workspace 존재·멤버 검사와 공개 스냅샷 검사를 거쳐 세션 검색(S1)과 같은 하이브리드 검색으로 청크 상위 top-k를
 * 고른다. 세션·메시지·턴 검사가 없고 아무것도 저장하지 않는다 — 에이전트가 한 질문에 여러 번 검색해도 된다. LLM은 부르지 않는다.
 */
@Service
@RequiredArgsConstructor
public class WorkspaceSearchService {
    private final WorkspaceQueryService workspaceQueryService;
    private final PublishedDocumentSearchService documentSearchService;

    public WorkspaceSearchResult search(
            long workspaceId,
            long memberId,
            String content
    ) {
        // 현행 Workspace 조회 API의 존재(404)·멤버(403) 검사를 그대로 쓴다(기획서 6.4 오류 행).
        workspaceQueryService.findDetail(
                workspaceId,
                memberId
        );
        requirePublishedSnapshot(workspaceId);
        // 검색 질의는 이력 없이 현재 질문뿐이다. 후속 질문 문맥은 에이전트가 자기 대화에서 유지한다.
        SearchContext searchContext = search(
                workspaceId,
                content
        );
        if (searchContext.isReady()) {
            return WorkspaceSearchResult.ready(
                    SearchContext.groundingRules(),
                    searchContext.contextReferences()
            );
        }
        return WorkspaceSearchResult.fallback(
                searchContext.status(),
                searchContext.fallbackAnswer()
        );
    }

    private void requirePublishedSnapshot(long workspaceId) {
        try {
            documentSearchService.requirePublishedSnapshot(workspaceId);
        } catch (SearchException exception) {
            throw translate(exception);
        }
    }

    private SearchContext search(
            long workspaceId,
            String query
    ) {
        try {
            return documentSearchService.search(
                    workspaceId,
                    query
            );
        } catch (SearchException exception) {
            throw translate(exception);
        }
    }

    /** 문서 준비 게이트만 채팅 코드로 바꾸고, 나머지 검색 오류는 코드를 그대로 데스크톱에 보낸다(로드맵 Q31). */
    private RuntimeException translate(SearchException exception) {
        if (exception.searchErrorCode() == SearchErrorCode.SEARCH_IMPORT_NOT_READY) {
            return new ChatException(
                    ChatErrorCode.CHAT_DOCUMENTS_NOT_READY,
                    exception
            );
        }
        return exception;
    }
}
