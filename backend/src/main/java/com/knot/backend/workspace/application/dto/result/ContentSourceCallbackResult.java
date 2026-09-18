package com.knot.backend.workspace.application.dto.result;

public record ContentSourceCallbackResult(
        boolean connected,
        Long workspaceId
) {

    public static ContentSourceCallbackResult connected(Long workspaceId) {
        return new ContentSourceCallbackResult(
                true,
                workspaceId
        );
    }

    public static ContentSourceCallbackResult failed(Long workspaceId) {
        return new ContentSourceCallbackResult(
                false,
                workspaceId
        );
    }
}
