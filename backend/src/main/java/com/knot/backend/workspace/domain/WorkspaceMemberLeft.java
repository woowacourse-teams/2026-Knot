package com.knot.backend.workspace.domain;

import java.time.Instant;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class WorkspaceMemberLeft {
    private final long workspaceId;
    private final long memberId;
    private final Instant leftAt;
}
