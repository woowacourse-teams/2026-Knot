package com.knot.backend.chat.application;

import com.knot.backend.chat.domain.ChatErrorCode;
import com.knot.backend.chat.domain.ChatException;
import com.knot.backend.chat.domain.ChatSession;
import com.knot.backend.chat.domain.ChatSessionRepository;
import com.knot.backend.workspace.domain.WorkspaceMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ChatSessionAccessPolicy {
    private final ChatSessionRepository chatSessionRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;

    public void requireWorkspaceMember(
            long workspaceId,
            long memberId
    ) {
        if (workspaceId <= 0) {
            throw new ChatException(ChatErrorCode.INVALID_CHAT_SESSION_WORKSPACE_ID);
        }
        if (memberId <= 0) {
            throw new ChatException(ChatErrorCode.INVALID_CHAT_SESSION_MEMBER_ID);
        }
        if (!workspaceMemberRepository.existsByWorkspaceIdAndMemberId(
                workspaceId,
                memberId
        )) {
            throw new ChatException(ChatErrorCode.CHAT_ACCESS_DENIED);
        }
    }

    public ChatSession requireOwner(
            long sessionId,
            long memberId
    ) {
        if (sessionId <= 0) {
            throw new ChatException(ChatErrorCode.CHAT_SESSION_NOT_FOUND);
        }
        if (memberId <= 0) {
            throw new ChatException(ChatErrorCode.INVALID_CHAT_SESSION_MEMBER_ID);
        }

        ChatSession chatSession = chatSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ChatException(ChatErrorCode.CHAT_SESSION_NOT_FOUND));
        if (chatSession.getMemberId() != memberId || !workspaceMemberRepository.existsByWorkspaceIdAndMemberId(
                chatSession.getWorkspaceId(),
                memberId
        )) {
            throw new ChatException(ChatErrorCode.CHAT_ACCESS_DENIED);
        }
        return chatSession;
    }
}
