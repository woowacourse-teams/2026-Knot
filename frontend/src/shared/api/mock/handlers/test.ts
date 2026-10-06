import { GetCsrfTokenResponseDto, GetMeResponseDto } from "@api/dto/auth";
import { GetChatMessagesResponseDto } from "@api/dto/chatMessage";
import {
  GetNotionConnectionResponseDto,
  PostNotionOAuthAuthorizationResponseDto,
} from "@api/dto/notionConnection";
import {
  GetChatSessionsResponseDto,
  PostChatSessionResponseDto,
} from "@api/dto/chatSession";
import { GetDocumentResponseDto } from "@api/dto/document";
import {
  PostRecordingAudioUploadUrlResponseDto,
  PostRecordingEndResponseDto,
  PostRecordingPauseResponseDto,
  PostRecordingResponseDto,
  PostRecordingResumeResponseDto,
} from "@api/dto/recording";
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
import { getDocumentApi } from "@api/fetch/api/v1/workspaces/[workspaceId]/documents/[documentId]";
import { getWorkspaceInvitationApi } from "@api/fetch/api/v1/workspaces/[workspaceId]/invitation";
import { getNotionConnectionApi } from "@api/fetch/api/v1/workspaces/[workspaceId]/notionConnection";
import { startNotionOAuthApi } from "@api/fetch/api/v1/workspaces/[workspaceId]/notionOauthAuthorizations";
import { issueWorkspaceInvitationApi } from "@api/fetch/api/v1/workspaces/[workspaceId]/invitations";
import { reissueWorkspaceInvitationApi } from "@api/fetch/api/v1/workspaces/[workspaceId]/invitations/reissue";
import { startRecordingApi } from "@api/fetch/api/v1/workspaces/[workspaceId]/recordings";
import {
  issueRecordingAudioUploadUrlApi,
  uploadRecordingAudioApi,
} from "@api/fetch/api/v1/workspaces/[workspaceId]/recordings/[recordingId]/audioUploadUrl";
import { endRecordingApi } from "@api/fetch/api/v1/workspaces/[workspaceId]/recordings/[recordingId]/end";
import { pauseRecordingApi } from "@api/fetch/api/v1/workspaces/[workspaceId]/recordings/[recordingId]/pause";
import { resumeRecordingApi } from "@api/fetch/api/v1/workspaces/[workspaceId]/recordings/[recordingId]/resume";
import { csrfTokenResponse, meResponse } from "@api/mock/responses/auth";
import {
  notionConnectionResponse,
  notionOAuthAuthorizationResponse,
} from "@api/mock/responses/notionConnection";
import { chatMessagesResponse } from "@api/mock/responses/chatMessage";
import {
  chatSessionResponse,
  chatSessionsResponse,
} from "@api/mock/responses/chatSession";
import { documentDetailsResponse } from "@api/mock/responses/document";
import {
  recordingAudioUploadUrlResponse,
  recordingEndResponse,
  recordingPauseResponse,
  recordingResumeResponse,
  recordingStartResponse,
} from "@api/mock/responses/recording";
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
const RECORDING_ID = 10;

const CONTROL_PROOF = {
  tabId: "6f1c2a4e-1b2d-4c3e-9f80-1a2b3c4d5e6f",
  controlToken: "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
};

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

  describe("문서", () => {
    it("GET /api/v1/workspaces/:workspaceId/documents/:documentId는 documentDetailsResponse에서 그 id의 문서를 돌려준다", async () => {
      const [document] = documentDetailsResponse;

      await expect(
        getDocumentApi({ workspaceId: WORKSPACE_ID, documentId: document.id }),
      ).resolves.toEqual(new GetDocumentResponseDto(document));
    });

    it("documentDetailsResponse에 없는 id면 404 DOCUMENT_NOT_FOUND로 답한다", async () => {
      await expect(
        getDocumentApi({ workspaceId: WORKSPACE_ID, documentId: 999 }),
      ).rejects.toMatchObject({
        response: { status: 404, data: { code: "DOCUMENT_NOT_FOUND" } },
      });
    });
  });

  describe("Notion 연결", () => {
    it("POST /api/v1/workspaces/:workspaceId/notion-oauth-authorizations는 notionOAuthAuthorizationResponse를 돌려준다", async () => {
      await expect(startNotionOAuthApi(WORKSPACE_ID)).resolves.toEqual(
        new PostNotionOAuthAuthorizationResponseDto(
          notionOAuthAuthorizationResponse,
        ),
      );
    });

    it("GET /api/v1/workspaces/:workspaceId/notion-connection은 notionConnectionResponse를 돌려준다", async () => {
      await expect(getNotionConnectionApi(WORKSPACE_ID)).resolves.toEqual(
        new GetNotionConnectionResponseDto(notionConnectionResponse),
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

  describe("녹음", () => {
    it("POST /api/v1/workspaces/:workspaceId/recordings는 recordingStartResponse를 돌려준다", async () => {
      await expect(
        startRecordingApi({
          workspaceId: WORKSPACE_ID,
          body: {
            requestId: "0b8e0c55-6f0a-4d0b-8f1e-2f6a8f3c9d10",
            ...CONTROL_PROOF,
          },
        }),
      ).resolves.toEqual(new PostRecordingResponseDto(recordingStartResponse));
    });

    it("POST .../recordings/:recordingId/pause는 recordingPauseResponse를 돌려준다", async () => {
      await expect(
        pauseRecordingApi({
          workspaceId: WORKSPACE_ID,
          recordingId: RECORDING_ID,
          body: CONTROL_PROOF,
        }),
      ).resolves.toEqual(
        new PostRecordingPauseResponseDto(recordingPauseResponse),
      );
    });

    it("POST .../recordings/:recordingId/resume은 recordingResumeResponse를 돌려준다", async () => {
      await expect(
        resumeRecordingApi({
          workspaceId: WORKSPACE_ID,
          recordingId: RECORDING_ID,
          body: CONTROL_PROOF,
        }),
      ).resolves.toEqual(
        new PostRecordingResumeResponseDto(recordingResumeResponse),
      );
    });

    it("POST .../recordings/:recordingId/end는 recordingEndResponse를 돌려준다", async () => {
      await expect(
        endRecordingApi({
          workspaceId: WORKSPACE_ID,
          recordingId: RECORDING_ID,
        }),
      ).resolves.toEqual(new PostRecordingEndResponseDto(recordingEndResponse));
    });

    it("POST .../recordings/:recordingId/audio-upload-url은 recordingAudioUploadUrlResponse를 돌려준다", async () => {
      await expect(
        issueRecordingAudioUploadUrlApi({
          workspaceId: WORKSPACE_ID,
          recordingId: RECORDING_ID,
          body: { contentType: "audio/webm", contentLength: 4 },
        }),
      ).resolves.toEqual(
        new PostRecordingAudioUploadUrlResponseDto(
          recordingAudioUploadUrlResponse,
        ),
      );
    });

    it("발급한 업로드 URL로 오디오를 PUT하면 본문 없이 성공한다", async () => {
      await expect(
        uploadRecordingAudioApi({
          uploadUrl: recordingAudioUploadUrlResponse.uploadUrl,
          audio: new Blob(["test"], { type: "audio/webm" }),
        }),
      ).resolves.toBeUndefined();
    });
  });
});
