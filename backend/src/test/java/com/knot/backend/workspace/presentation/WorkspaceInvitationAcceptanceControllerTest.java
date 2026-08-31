package com.knot.backend.workspace.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.knot.backend.auth.domain.AuthenticatedMember;
import com.knot.backend.workspace.application.WorkspaceInvitationAcceptanceService;
import com.knot.backend.workspace.application.dto.result.WorkspaceInvitationAcceptanceResult;
import com.knot.backend.workspace.presentation.dto.request.WorkspaceInvitationAcceptanceRequest;
import com.knot.backend.workspace.presentation.dto.response.WorkspaceInvitationAcceptanceResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

class WorkspaceInvitationAcceptanceControllerTest {
    private static final Long WORKSPACE_ID = 1L;
    private static final String WORKSPACE_NAME = "Knot 팀";
    private static final String CREDENTIAL = "X35D3S";
    private static final String REMOTE_ADDRESS = "203.0.113.10";
    private static final long MEMBER_ID = 2L;

    private final WorkspaceInvitationAcceptanceService service = mock(WorkspaceInvitationAcceptanceService.class);
    private final HttpServletRequest httpServletRequest = mock(HttpServletRequest.class);
    private final WorkspaceInvitationAcceptanceController controller = new WorkspaceInvitationAcceptanceController(
            service
    );
    private final AuthenticatedMember authenticatedMember = AuthenticatedMember.of(
            MEMBER_ID,
            "현성",
            null
    );

    @DisplayName("새 멤버십을 만들면 201과 최소 워크스페이스 응답을 반환한다")
    @Test
    void accept_success_created() {
        // given
        WorkspaceInvitationAcceptanceRequest request = new WorkspaceInvitationAcceptanceRequest(CREDENTIAL);
        when(httpServletRequest.getRemoteAddr()).thenReturn(REMOTE_ADDRESS);
        when(
                service.accept(
                        CREDENTIAL,
                        REMOTE_ADDRESS,
                        MEMBER_ID
                )
        ).thenReturn(result(true));

        // when
        ResponseEntity<WorkspaceInvitationAcceptanceResponse> response = controller.accept(
                request,
                httpServletRequest,
                authenticatedMember
        );

        // then
        assertThat(
                response.getStatusCode()
                        .value()
        ).isEqualTo(201);
        assertThat(response.getBody()).isEqualTo(expectedResponse());
        verify(service).accept(
                CREDENTIAL,
                REMOTE_ADDRESS,
                MEMBER_ID
        );
    }

    @DisplayName("기존 멤버십이면 200과 최소 워크스페이스 응답을 반환한다")
    @Test
    void accept_success_existingMember() {
        // given
        WorkspaceInvitationAcceptanceRequest request = new WorkspaceInvitationAcceptanceRequest(CREDENTIAL);
        when(httpServletRequest.getRemoteAddr()).thenReturn(REMOTE_ADDRESS);
        when(
                service.accept(
                        CREDENTIAL,
                        REMOTE_ADDRESS,
                        MEMBER_ID
                )
        ).thenReturn(result(false));

        // when
        ResponseEntity<WorkspaceInvitationAcceptanceResponse> response = controller.accept(
                request,
                httpServletRequest,
                authenticatedMember
        );

        // then
        assertThat(
                response.getStatusCode()
                        .value()
        ).isEqualTo(200);
        assertThat(response.getBody()).isEqualTo(expectedResponse());
    }

    private WorkspaceInvitationAcceptanceResult result(boolean created) {
        return new WorkspaceInvitationAcceptanceResult(
                WORKSPACE_ID,
                WORKSPACE_NAME,
                created
        );
    }

    private WorkspaceInvitationAcceptanceResponse expectedResponse() {
        return new WorkspaceInvitationAcceptanceResponse(
                WORKSPACE_ID,
                WORKSPACE_NAME
        );
    }
}
