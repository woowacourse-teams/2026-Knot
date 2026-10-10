package com.knot.backend.search.application;

import com.knot.backend.search.application.dto.query.SearchConversationListParameters;
import com.knot.backend.search.application.dto.result.SearchConversationListItemResult;
import com.knot.backend.search.application.dto.result.SearchConversationListResult;
import com.knot.backend.search.domain.SearchConversationCursor;
import com.knot.backend.search.domain.SearchErrorCode;
import com.knot.backend.search.domain.SearchException;
import com.knot.backend.workspace.domain.WorkspaceErrorCode;
import com.knot.backend.workspace.domain.WorkspaceException;
import com.knot.backend.workspace.domain.WorkspaceMemberRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class SearchConversationListService {

    private final WorkspaceMemberRepository memberships;
    private final SearchConversationListQuery query;

    public SearchConversationListResult find(
            long workspaceId,
            long memberId,
            SearchConversationListParameters parameters
    ) {
        validateWorkspaceId(workspaceId);
        validateMembership(
                workspaceId,
                memberId
        );
        SearchConversationCursor cursor = parseCursor(
                workspaceId,
                memberId,
                parameters.cursor()
        );
        List<SearchConversationListItemResult> page = query.findPage(
                workspaceId,
                memberId,
                parameters.size(),
                cursor
        );
        return new SearchConversationListResult(
                limitItems(
                        page,
                        parameters.size()
                ),
                createNextCursor(
                        workspaceId,
                        memberId,
                        page,
                        parameters.size()
                )
        );
    }

    private void validateWorkspaceId(long workspaceId) {
        if (workspaceId <= 0) {
            throw new SearchException(SearchErrorCode.INVALID_PARAMETER);
        }
    }

    private void validateMembership(
            long workspaceId,
            long memberId
    ) {
        if (!memberships.existsByWorkspaceIdAndMemberId(
                workspaceId,
                memberId
        )) {
            throw new WorkspaceException(WorkspaceErrorCode.WORKSPACE_ACCESS_DENIED);
        }
    }

    private SearchConversationCursor parseCursor(
            long workspaceId,
            long memberId,
            String encoded
    ) {
        if (encoded == null) {
            return null;
        }
        return SearchConversationCursor.parse(
                encoded,
                workspaceId,
                memberId
        );
    }

    private List<SearchConversationListItemResult> limitItems(
            List<SearchConversationListItemResult> page,
            int size
    ) {
        if (page.size() > size) {
            return page.subList(
                    0,
                    size
            );
        }
        return page;
    }

    private String createNextCursor(
            long workspaceId,
            long memberId,
            List<SearchConversationListItemResult> page,
            int size
    ) {
        if (page.size() <= size) {
            return null;
        }
        SearchConversationListItemResult last = page.get(size - 1);
        return SearchConversationCursor.of(
                workspaceId,
                memberId,
                last.updatedAt(),
                last.id()
        )
                .encode();
    }
}
