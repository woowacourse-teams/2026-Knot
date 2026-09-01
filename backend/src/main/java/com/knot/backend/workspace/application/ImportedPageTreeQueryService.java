package com.knot.backend.workspace.application;

import com.knot.backend.workspace.application.dto.result.ImportedPageTreeItemResult;
import com.knot.backend.workspace.domain.ImportedPageErrorCode;
import com.knot.backend.workspace.domain.ImportedPageException;
import com.knot.backend.workspace.domain.ImportedPageMetadata;
import com.knot.backend.workspace.domain.ImportedPageRepository;
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
public class ImportedPageTreeQueryService {
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final ImportedPageRepository importedPageRepository;

    public List<ImportedPageTreeItemResult> findTree(
            Long workspaceId,
            long memberId
    ) {
        validateWorkspaceId(workspaceId);
        validateWorkspaceExists(workspaceId);
        validateWorkspaceMember(
                workspaceId,
                memberId
        );
        List<ImportedPageMetadata> importedPages = importedPageRepository
                .findPublishedMetadataByWorkspaceIdOrderByPositionAscIdAsc(workspaceId);
        validateTree(
                workspaceId,
                importedPages
        );
        return importedPages.stream()
                .map(ImportedPageTreeItemResult::from)
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
            List<ImportedPageMetadata> importedPages
    ) {
        Map<Long, Long> parentPageIdById = new HashMap<>();
        for (ImportedPageMetadata importedPage : importedPages) {
            Long id = importedPage.id();
            if (id == null || id <= 0 || !Objects.equals(
                    importedPage.workspaceId(),
                    workspaceId
            ) || parentPageIdById.containsKey(id) || hasInconsistentParentReference(importedPage)) {
                throw invalidTree();
            }
            parentPageIdById.put(
                    id,
                    importedPage.parentId()
            );
        }
        validateParentReferences(parentPageIdById);
        validateNoCycle(parentPageIdById);
    }

    private boolean hasInconsistentParentReference(ImportedPageMetadata importedPage) {
        return importedPage.hasParentReference() != (importedPage.parentId() != null);
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

    private ImportedPageException invalidTree() {
        return new ImportedPageException(ImportedPageErrorCode.IMPORTED_PAGE_TREE_INVALID);
    }
}
