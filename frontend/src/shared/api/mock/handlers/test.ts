import { GetCsrfTokenResponseDto, GetMeResponseDto } from "@api/dto/auth";
import { GetChatMessagesResponseDto } from "@api/dto/chatMessage";
import { PostDocumentGenerationJobRetryResponseDto } from "@api/dto/documentGenerationJob";
import {
  GetNotionConnectionResponseDto,
  PostNotionOAuthAuthorizationResponseDto,
} from "@api/dto/notionConnection";
import {
  GetChatSessionsResponseDto,
  PostChatSessionResponseDto,
} from "@api/dto/chatSession";
import {
  GetRecordingResponseDto,
  PostRecordingAudioUploadCompleteResponseDto,
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
import { getWorkspaceInvitationApi } from "@api/fetch/api/v1/workspaces/[workspaceId]/invitation";
import { getNotionConnectionApi } from "@api/fetch/api/v1/workspaces/[workspaceId]/notionConnection";
import { startNotionOAuthApi } from "@api/fetch/api/v1/workspaces/[workspaceId]/notionOauthAuthorizations";
import { issueWorkspaceInvitationApi } from "@api/fetch/api/v1/workspaces/[workspaceId]/invitations";
import { reissueWorkspaceInvitationApi } from "@api/fetch/api/v1/workspaces/[workspaceId]/invitations/reissue";
import { retryDocumentGenerationJobApi } from "@api/fetch/api/v1/workspaces/[workspaceId]/documentGenerationJobs/[jobId]/retry";
import { startRecordingApi } from "@api/fetch/api/v1/workspaces/[workspaceId]/recordings";
import { getRecordingApi } from "@api/fetch/api/v1/workspaces/[workspaceId]/recordings/[recordingId]";
import { completeRecordingAudioUploadApi } from "@api/fetch/api/v1/workspaces/[workspaceId]/recordings/[recordingId]/audioUploadComplete";
import {
  issueRecordingAudioUploadUrlApi,
  uploadRecordingAudioApi,
} from "@api/fetch/api/v1/workspaces/[workspaceId]/recordings/[recordingId]/audioUploadUrl";
import { endRecordingApi } from "@api/fetch/api/v1/workspaces/[workspaceId]/recordings/[recordingId]/end";
import { pauseRecordingApi } from "@api/fetch/api/v1/workspaces/[workspaceId]/recordings/[recordingId]/pause";
import { resumeRecordingApi } from "@api/fetch/api/v1/workspaces/[workspaceId]/recordings/[recordingId]/resume";
import { HTTP_ERROR_TYPE } from "@api/httpClient/error";
import { csrfTokenResponse, meResponse } from "@api/mock/responses/auth";
import { documentGenerationJobRetryResponse } from "@api/mock/responses/documentGenerationJob";
import {
  notionConnectionResponse,
  notionOAuthAuthorizationResponse,
} from "@api/mock/responses/notionConnection";
import { chatMessagesResponse } from "@api/mock/responses/chatMessage";
import {
  chatSessionResponse,
  chatSessionsResponse,
} from "@api/mock/responses/chatSession";
import {
  recordingAudioUploadCompleteResponse,
  recordingAudioUploadUrlResponse,
  recordingDetailsResponse,
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

    it("POST .../recordings/:recordingId/audio-upload-complete는 recordingAudioUploadCompleteResponse를 돌려준다", async () => {
      await expect(
        completeRecordingAudioUploadApi({
          workspaceId: WORKSPACE_ID,
          recordingId: RECORDING_ID,
          body: { uploadId: recordingAudioUploadUrlResponse.uploadId },
        }),
      ).resolves.toEqual(
        new PostRecordingAudioUploadCompleteResponseDto(
          recordingAudioUploadCompleteResponse,
        ),
      );
    });

    it("GET .../recordings/:recordingId는 recordingDetailsResponse에서 그 녹음을 찾아 돌려준다", async () => {
      // 상태가 서로 다른 녹음을 모두 물어, 핸들러가 id로 골라 주는지 확인해요
      for (const recording of recordingDetailsResponse) {
        await expect(
          getRecordingApi({
            workspaceId: WORKSPACE_ID,
            recordingId: recording.recordingId,
          }),
        ).resolves.toEqual(new GetRecordingResponseDto(recording));
      }
    });

    it("recordingDetailsResponse에 없는 녹음을 물으면 404 RECORDING_NOT_FOUND로 답한다", async () => {
      await expect(
        getRecordingApi({ workspaceId: WORKSPACE_ID, recordingId: 999 }),
      ).rejects.toMatchObject({
        type: HTTP_ERROR_TYPE.notFound,
        code: "RECORDING_NOT_FOUND",
      });
    });
  });

  describe("문서 생성 작업", () => {
    // 정리 중인 녹음(작업 진행 중)과 문서 만들기에 실패한 녹음(작업 실패)
    const [processingRecording, , failedRecording] = recordingDetailsResponse;
    const failedJobParams = {
      workspaceId: WORKSPACE_ID,
      jobId: documentGenerationJobRetryResponse.jobId,
    };
    const failedRecordingParams = {
      workspaceId: WORKSPACE_ID,
      recordingId: failedRecording.recordingId,
    };

    it("POST .../document-generation-jobs/:jobId/retry는 실패한 작업을 접수하고 documentGenerationJobRetryResponse를 돌려준다", async () => {
      await expect(
        retryDocumentGenerationJobApi(failedJobParams),
      ).resolves.toEqual(
        new PostDocumentGenerationJobRetryResponseDto(
          documentGenerationJobRetryResponse,
        ),
      );
    });

    it("다시 시도한 뒤에는 그 녹음의 상세가 정리 중으로 바뀌고 실패 사유가 지워진다", async () => {
      await retryDocumentGenerationJobApi(failedJobParams);

      await expect(
        getRecordingApi(failedRecordingParams),
      ).resolves.toMatchObject({
        status: "PROCESSING",
        documentGenerationStatus: "QUEUED",
        failureStage: null,
        failureReason: null,
      });
    });

    it("이미 다시 시도해 실패 상태가 아닌 작업을 또 시도하면 409 RETRY_NOT_ALLOWED로 답한다", async () => {
      await retryDocumentGenerationJobApi(failedJobParams);

      await expect(
        retryDocumentGenerationJobApi(failedJobParams),
      ).rejects.toMatchObject({
        type: HTTP_ERROR_TYPE.conflict,
        code: "RETRY_NOT_ALLOWED",
      });
    });

    it("진행 중인 작업을 다시 시도하면 409 RETRY_NOT_ALLOWED로 답한다", async () => {
      await expect(
        retryDocumentGenerationJobApi({
          workspaceId: WORKSPACE_ID,
          // mock 데이터에서 이 작업 ID가 사라지면 0이 되어 404로 실패하므로, 테스트가 알려 줘요
          jobId: Number(processingRecording.documentGenerationJobId),
        }),
      ).rejects.toMatchObject({
        type: HTTP_ERROR_TYPE.conflict,
        code: "RETRY_NOT_ALLOWED",
      });
    });

    it("없는 작업을 다시 시도하면 404 DOCUMENT_GENERATION_JOB_NOT_FOUND로 답한다", async () => {
      await expect(
        retryDocumentGenerationJobApi({
          workspaceId: WORKSPACE_ID,
          jobId: 999,
        }),
      ).rejects.toMatchObject({
        type: HTTP_ERROR_TYPE.notFound,
        code: "DOCUMENT_GENERATION_JOB_NOT_FOUND",
      });
    });

    it("다시 시도한 기록은 테스트가 끝나면 지워져, 다음 테스트는 기본 응답을 받는다", async () => {
      // 위 테스트들이 작업을 다시 시도했지만 vitest.setup의 afterEach가 기록을 지웠어요
      await expect(getRecordingApi(failedRecordingParams)).resolves.toEqual(
        new GetRecordingResponseDto(failedRecording),
      );
    });
  });
});
