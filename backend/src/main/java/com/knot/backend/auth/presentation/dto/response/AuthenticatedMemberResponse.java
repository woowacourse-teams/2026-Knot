package com.knot.backend.auth.presentation.dto.response;

import com.knot.backend.auth.domain.AuthenticatedMember;

public record AuthenticatedMemberResponse(
        long memberId,
        long githubId,
        String nickname,
        String profileImageUrl
) {

    public static AuthenticatedMemberResponse from(AuthenticatedMember member) {
        return new AuthenticatedMemberResponse(
                member.getMemberId(),
                member.getGithubId(),
                member.getNickname(),
                member.getProfileImageUrl()
        );
    }
}
