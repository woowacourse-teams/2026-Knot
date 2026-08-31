package com.knot.backend.notion.application.dto.result;

public record NotionOAuthToken(
        String accessToken,
        String refreshToken,
        String notionWorkspaceId,
        String notionWorkspaceName,
        String notionWorkspaceIcon,
        String botId,
        String ownerType,
        String ownerUserId,
        String duplicatedTemplateId,
        String requestId
) {
}
