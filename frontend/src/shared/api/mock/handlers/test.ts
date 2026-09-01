import { GetCsrfTokenResponseDto, GetMeResponseDto } from "@api/dto/auth";
import { GetChatMessagesResponseDto } from "@api/dto/chatMessage";
import {
  GetChatSessionsResponseDto,
  PostChatSessionResponseDto,
} from "@api/dto/chatSession";
import {
  GetWorkspaceResponseDto,
  GetWorkspacesResponseDto,
  PostWorkspaceResponseDto,
} from "@api/dto/workspace";
import {
  GetInvitationPreviewResponseDto,
  GetWorkspaceInvitationResponseDto,
  PostInvitationAcceptResponseDto,
  PostWorkspaceInvitationReissueResponseDto,
  PostWorkspaceInvitationResponseDto,
} from "@api/dto/workspaceInvitation";
import { getCsrfTokenApi } from "@api/fetch/api/v1/auth/csrf";
import { getMeApi } from "@api/fetch/api/v1/auth/me";
import { completeNicknameApi } from "@api/fetch/api/v1/auth/nickname";
import { getChatMessagesApi } from "@api/fetch/api/v1/conversations/[sessionId]";
import { getInvitationPreviewApi } from "@api/fetch/api/v1/invitations/[tokenOrCode]";
import { acceptInvitationApi } from "@api/fetch/api/v1/invitations/accept";
import { updateLastViewedWorkspaceApi } from "@api/fetch/api/v1/members/me/lastViewedWorkspace";
import {
  createWorkspaceApi,
  getWorkspacesApi,
} from "@api/fetch/api/v1/workspaces";
import { getWorkspaceApi } from "@api/fetch/api/v1/workspaces/[workspaceId]";
import {
  createChatSessionApi,
  getChatSessionsApi,
} from "@api/fetch/api/v1/workspaces/[workspaceId]/conversations";
import { getWorkspaceInvitationApi } from "@api/fetch/api/v1/workspaces/[workspaceId]/invitation";
import { issueWorkspaceInvitationApi } from "@api/fetch/api/v1/workspaces/[workspaceId]/invitations";
import { reissueWorkspaceInvitationApi } from "@api/fetch/api/v1/workspaces/[workspaceId]/invitations/reissue";
import { csrfTokenResponse, meResponse } from "@api/mock/responses/auth";
import { chatMessagesResponse } from "@api/mock/responses/chatMessage";
import {
  chatSessionResponse,
  chatSessionsResponse,
} from "@api/mock/responses/chatSession";
import {
  workspaceCreateResponse,
  workspaceDetailResponse,
  workspacesResponse,
} from "@api/mock/responses/workspace";
import {
  invitationAcceptanceResponse,
  invitationPreviewResponse,
  workspaceInvitationResponse,
} from "@api/mock/responses/workspaceInvitation";
import { describe, expect, it } from "vitest";

const WORKSPACE_ID = 1;
const SESSION_ID = 100;

// 기본 핸들러가 fetch 요청 함수와 같은 경로·메서드에 응답하는지 확인해요
// 기대값은 mock 응답을 응답 DTO로 변환한 값이에요 (test-strategy.md 「기대값」)
describe("mock 기본 핸들러와 fetch 요청 함수의 대응", () => {
  describe("인증", () => {
    it("GET /api/v1/auth/me는 meResponse를 돌려준다", async () => {
      await expect(getMeApi()).resolves.toEqual(
        new GetMeResponseDto(meResponse),
      );
    });

    it("GET /api/v1/auth/csrf는 csrfTokenResponse를 돌려준다", async () => {
      await expect(getCsrfTokenApi()).resolves.toEqual(
        new GetCsrfTokenResponseDto(csrfTokenResponse),
      );
    });

    it("POST /api/v1/auth/nickname은 본문 없이 성공한다", async () => {
      await expect(
        completeNicknameApi({ nickname: "노티드" }),
      ).resolves.toBeUndefined();
    });
  });

  describe("워크스페이스", () => {
    it("GET /api/v1/workspaces는 workspacesResponse를 돌려준다", async () => {
      await expect(getWorkspacesApi()).resolves.toEqual(
        new GetWorkspacesResponseDto(workspacesResponse),
      );
    });

    it("POST /api/v1/workspaces는 workspaceCreateResponse를 돌려준다", async () => {
      await expect(createWorkspaceApi({ name: "Knot 팀" })).resolves.toEqual(
        new PostWorkspaceResponseDto(workspaceCreateResponse),
      );
    });

    it("GET /api/v1/workspaces/:workspaceId는 workspaceDetailResponse를 돌려준다", async () => {
      await expect(getWorkspaceApi(WORKSPACE_ID)).resolves.toEqual(
        new GetWorkspaceResponseDto(workspaceDetailResponse),
      );
    });

    it("PUT /api/v1/members/me/last-viewed-workspace는 본문 없이 성공한다", async () => {
      await expect(
        updateLastViewedWorkspaceApi({ workspaceId: WORKSPACE_ID }),
      ).resolves.toBeUndefined();
    });
  });

  describe("워크스페이스 초대", () => {
    it("GET /api/v1/workspaces/:workspaceId/invitation은 workspaceInvitationResponse를 돌려준다", async () => {
      await expect(getWorkspaceInvitationApi(WORKSPACE_ID)).resolves.toEqual(
        new GetWorkspaceInvitationResponseDto(workspaceInvitationResponse),
      );
    });

    it("POST /api/v1/workspaces/:workspaceId/invitations는 workspaceInvitationResponse를 돌려준다", async () => {
      await expect(issueWorkspaceInvitationApi(WORKSPACE_ID)).resolves.toEqual(
        new PostWorkspaceInvitationResponseDto(workspaceInvitationResponse),
      );
    });

    it("POST /api/v1/workspaces/:workspaceId/invitations/reissue는 workspaceInvitationResponse를 돌려준다", async () => {
      await expect(
        reissueWorkspaceInvitationApi(WORKSPACE_ID),
      ).resolves.toEqual(
        new PostWorkspaceInvitationReissueResponseDto(
          workspaceInvitationResponse,
        ),
      );
    });

    it("GET /api/v1/invitations/:tokenOrCode는 invitationPreviewResponse를 돌려준다", async () => {
      await expect(
        getInvitationPreviewApi(workspaceInvitationResponse.code),
      ).resolves.toEqual(
        new GetInvitationPreviewResponseDto(invitationPreviewResponse),
      );
    });

    it("POST /api/v1/invitations/accept는 invitationAcceptanceResponse를 돌려준다", async () => {
      await expect(
        acceptInvitationApi({ credential: workspaceInvitationResponse.code }),
      ).resolves.toEqual(
        new PostInvitationAcceptResponseDto(invitationAcceptanceResponse),
      );
    });
  });

  describe("대화", () => {
    it("GET /api/v1/workspaces/:workspaceId/conversations는 chatSessionsResponse를 돌려준다", async () => {
      await expect(getChatSessionsApi(WORKSPACE_ID)).resolves.toEqual(
        new GetChatSessionsResponseDto(chatSessionsResponse),
      );
    });

    it("POST /api/v1/workspaces/:workspaceId/conversations는 chatSessionResponse를 돌려준다", async () => {
      await expect(
        createChatSessionApi(WORKSPACE_ID, { title: "새 대화" }),
      ).resolves.toEqual(new PostChatSessionResponseDto(chatSessionResponse));
    });

    it("GET /api/v1/conversations/:sessionId는 chatMessagesResponse를 돌려준다", async () => {
      await expect(getChatMessagesApi(SESSION_ID)).resolves.toEqual(
        new GetChatMessagesResponseDto(chatMessagesResponse),
      );
    });
  });
});
