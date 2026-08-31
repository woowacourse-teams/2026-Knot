package com.knot.backend.workspace.application;

import com.knot.backend.workspace.application.dto.result.NotionPageTreeItemResult;
import com.knot.backend.workspace.domain.NotionPageErrorCode;
import com.knot.backend.workspace.domain.NotionPageException;
import com.knot.backend.workspace.domain.NotionPageMetadata;
import com.knot.backend.workspace.domain.NotionPageRepository;
import com.knot.backend.workspace.domain.WorkspaceErrorCode;
import com.knot.backend.workspace.domain.WorkspaceException;
import com.knot.backend.workspace.domain.WorkspaceMemberRepository;
import com.knot.backend.workspace.domain.WorkspaceRepository;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotionPageTreeQueryService {
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final NotionPageRepository notionPageRepository;

    public List<NotionPageTreeItemResult> findTree(
            Long workspaceId,
            long memberId
    ) {
        validateWorkspaceId(workspaceId);
        validateWorkspaceExists(workspaceId);
        validateWorkspaceMember(
                workspaceId,
                memberId
        );
        List<NotionPageMetadata> notionPages = notionPageRepository
                .findPublishedMetadataByWorkspaceIdOrderByPositionAscIdAsc(workspaceId);
        validateTree(
                workspaceId,
                notionPages
        );
        return notionPages.stream()
                .map(NotionPageTreeItemResult::from)
                .toList();
    }

    private void validateWorkspaceId(Long workspaceId) {
        if (workspaceId == null || workspaceId <= 0) {
            throw new WorkspaceException(WorkspaceErrorCode.INVALID_WORKSPACE_ID);
        }
    }

    private void validateWorkspaceExists(Long workspaceId) {
        if (workspaceRepository.findById(workspaceId)
                .isEmpty()) {
            throw new WorkspaceException(WorkspaceErrorCode.WORKSPACE_NOT_FOUND);
        }
    }

    private void validateWorkspaceMember(
            Long workspaceId,
            long memberId
    ) {
        if (!workspaceMemberRepository.existsByWorkspaceIdAndMemberId(
                workspaceId,
                memberId
        )) {
            throw new WorkspaceException(WorkspaceErrorCode.WORKSPACE_ACCESS_DENIED);
        }
    }

    private void validateTree(
            Long workspaceId,
            List<NotionPageMetadata> notionPages
    ) {
        Map<Long, Long> parentPageIdById = new HashMap<>();
        for (NotionPageMetadata notionPage : notionPages) {
            Long id = notionPage.id();
            if (id == null || id <= 0 || !Objects.equals(
                    notionPage.workspaceId(),
                    workspaceId
            ) || parentPageIdById.containsKey(id)) {
                throw invalidTree();
            }
            parentPageIdById.put(
                    id,
                    notionPage.parentPageId()
            );
        }
        validateParentReferences(parentPageIdById);
        validateNoCycle(parentPageIdById);
    }

    private void validateParentReferences(Map<Long, Long> parentPageIdById) {
        for (Map.Entry<Long, Long> entry : parentPageIdById.entrySet()) {
            Long id = entry.getKey();
            Long parentPageId = entry.getValue();
            if (parentPageId != null && (parentPageId.equals(id) || !parentPageIdById.containsKey(parentPageId))) {
                throw invalidTree();
            }
        }
    }

    private void validateNoCycle(Map<Long, Long> parentPageIdById) {
        Set<Long> validatedIds = new HashSet<>();
        for (Long id : parentPageIdById.keySet()) {
            Set<Long> path = new HashSet<>();
            Long currentId = id;
            while (currentId != null && !validatedIds.contains(currentId)) {
                if (!path.add(currentId)) {
                    throw invalidTree();
                }
                currentId = parentPageIdById.get(currentId);
            }
            validatedIds.addAll(path);
        }
    }

    private NotionPageException invalidTree() {
        return new NotionPageException(NotionPageErrorCode.NOTION_PAGE_TREE_INVALID);
    }
}
