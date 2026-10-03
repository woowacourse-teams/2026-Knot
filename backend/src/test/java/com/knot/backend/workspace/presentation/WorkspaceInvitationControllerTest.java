package com.knot.backend.workspace.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.knot.backend.auth.domain.AuthenticatedMember;
import com.knot.backend.workspace.application.WorkspaceInvitationService;
import com.knot.backend.workspace.application.dto.result.WorkspaceInvitationResult;
import com.knot.backend.workspace.presentation.dto.response.WorkspaceInvitationResponse;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WorkspaceInvitationControllerTest {
    private static final Long WORKSPACE_ID = 1L;
    private static final long MEMBER_ID = 2L;
    private static final String CODE = "ABCXYZ";
    private static final String LINK_TOKEN = "link-token";
    private static final Instant EXPIRES_AT = Instant.parse("2026-08-30T00:00:00Z");

    private final WorkspaceInvitationService service = mock(WorkspaceInvitationService.class);
    private final WorkspaceInvitationController controller = new WorkspaceInvitationController(service);
    private final AuthenticatedMember authenticatedMember = AuthenticatedMember.of(
            MEMBER_ID,
            "현성",
            null
    );

    @DisplayName("초대를 발급하면 응답 DTO를 반환한다")
    @Test
    void issue_success() {
        // given
        when(
                service.issue(
                        WORKSPACE_ID,
                        MEMBER_ID
                )
        ).thenReturn(
                new WorkspaceInvitationResult(
                        CODE,
                        LINK_TOKEN,
                        EXPIRES_AT
                )
        );

        // when
        WorkspaceInvitationResponse response = controller.issue(
                WORKSPACE_ID,
                authenticatedMember
        );

        // then
        assertThat(response).isEqualTo(
                new WorkspaceInvitationResponse(
                        CODE,
                        LINK_TOKEN,
                        EXPIRES_AT
                )
        );
    }
}
