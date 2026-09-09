package com.knot.backend.mcp.application;

import com.knot.backend.workspace.application.dto.result.WorkspaceListItemResult;

/** {@code list_workspaces}의 {@code structuredContent.workspaces} 항목. 현행 목록 API와 같이 id·name만 있다(로드맵 Q49). */
public record McpWorkspaceItem(
        long id,
        String name
) {

    public static McpWorkspaceItem from(WorkspaceListItemResult result) {
        return new McpWorkspaceItem(
                result.id(),
                result.name()
        );
    }
}
