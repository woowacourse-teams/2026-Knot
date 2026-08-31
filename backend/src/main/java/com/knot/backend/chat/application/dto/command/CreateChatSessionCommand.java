package com.knot.backend.chat.application.dto.command;

public record CreateChatSessionCommand(
        long workspaceId,
        long memberId,
        String title
) {
}
