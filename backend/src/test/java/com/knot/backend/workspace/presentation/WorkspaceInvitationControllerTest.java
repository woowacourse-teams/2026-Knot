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
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

class WorkspaceInvitationControllerTest {
    private static final Long WORKSPACE_ID = 1L;
    private static final long MEMBER_ID = 2L;
    private static final String CODE = "X35D3S";
    private static final String LINK_TOKEN = "link-token";
    private static final Instant EXPIRES_AT = Instant.parse("2026-08-30T00:00:00Z");

    private final WorkspaceInvitationService service = mock(WorkspaceInvitationService.class);
    private final WorkspaceInvitationController controller = new WorkspaceInvitationController(service);
    private final AuthenticatedMember authenticatedMember = AuthenticatedMember.of(
            MEMBER_ID,
            "현성",
            null
    );

    @DisplayName("새 초대를 발급하면 201과 조회 Location을 반환한다")
    @Test
    void issue_success_created() {
        // given
        when(
                service.issue(
                        WORKSPACE_ID,
                        MEMBER_ID
                )
        ).thenReturn(result(true));

        // when
        ResponseEntity<WorkspaceInvitationResponse> response = controller.issue(
                WORKSPACE_ID,
                authenticatedMember
        );

        // then
        assertThat(
                response.getStatusCode()
                        .value()
        ).isEqualTo(201);
        assertThat(
                response.getHeaders()
                        .getFirst(HttpHeaders.LOCATION)
        ).isEqualTo("/api/v1/workspaces/1/invitation");
        assertThat(response.getBody()).isEqualTo(expectedResponse());
    }

    @DisplayName("기존 활성 초대를 반환하면 200을 사용하고 Location을 추가하지 않는다")
    @Test
    void issue_success_existing() {
        // given
        when(
                service.issue(
                        WORKSPACE_ID,
                        MEMBER_ID
                )
        ).thenReturn(result(false));

        // when
        ResponseEntity<WorkspaceInvitationResponse> response = controller.issue(
                WORKSPACE_ID,
                authenticatedMember
        );

        // then
        assertThat(
                response.getStatusCode()
                        .value()
        ).isEqualTo(200);
        assertThat(
                response.getHeaders()
                        .getFirst(HttpHeaders.LOCATION)
        ).isNull();
        assertThat(response.getBody()).isEqualTo(expectedResponse());
    }

    @DisplayName("활성 초대를 조회하면 응답 DTO를 반환한다")
    @Test
    void get_success() {
        // given
        when(
                service.get(
                        WORKSPACE_ID,
                        MEMBER_ID
                )
        ).thenReturn(result(false));

        // when
        WorkspaceInvitationResponse response = controller.get(
                WORKSPACE_ID,
                authenticatedMember
        );

        // then
        assertThat(response).isEqualTo(expectedResponse());
    }

    @DisplayName("초대를 재발급하면 201과 조회 Location을 반환한다")
    @Test
    void reissue_success_created() {
        // given
        when(
                service.reissue(
                        WORKSPACE_ID,
                        MEMBER_ID
                )
        ).thenReturn(result(true));

        // when
        ResponseEntity<WorkspaceInvitationResponse> response = controller.reissue(
                WORKSPACE_ID,
                authenticatedMember
        );

        // then
        assertThat(
                response.getStatusCode()
                        .value()
        ).isEqualTo(201);
        assertThat(
                response.getHeaders()
                        .getFirst(HttpHeaders.LOCATION)
        ).isEqualTo("/api/v1/workspaces/1/invitation");
        assertThat(response.getBody()).isEqualTo(expectedResponse());
    }

    private WorkspaceInvitationResult result(boolean created) {
        return new WorkspaceInvitationResult(
                CODE,
                LINK_TOKEN,
                EXPIRES_AT,
                created
        );
    }

    private WorkspaceInvitationResponse expectedResponse() {
        return new WorkspaceInvitationResponse(
                CODE,
                LINK_TOKEN,
                EXPIRES_AT
        );
    }
}
