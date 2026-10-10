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
import {
  GetDocumentConfirmationsResponseDto,
  GetDocumentResponseDto,
  GetDocumentsResponseDto,
  PutDocumentConfirmationResponseDto,
} from "@api/dto/document";
import {
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
import {
  getAllDocumentsApi,
  getDocumentsApi,
} from "@api/fetch/api/v1/workspaces/[workspaceId]/documents";
import { getDocumentApi } from "@api/fetch/api/v1/workspaces/[workspaceId]/documents/[documentId]";
import { getDocumentConfirmationsApi } from "@api/fetch/api/v1/workspaces/[workspaceId]/documents/[documentId]/confirmations";
import { confirmDocumentApi } from "@api/fetch/api/v1/workspaces/[workspaceId]/documents/[documentId]/confirmations/me";
import { getWorkspaceInvitationApi } from "@api/fetch/api/v1/workspaces/[workspaceId]/invitation";
import { getNotionConnectionApi } from "@api/fetch/api/v1/workspaces/[workspaceId]/notionConnection";
import { startNotionOAuthApi } from "@api/fetch/api/v1/workspaces/[workspaceId]/notionOauthAuthorizations";
import { issueWorkspaceInvitationApi } from "@api/fetch/api/v1/workspaces/[workspaceId]/invitations";
import { reissueWorkspaceInvitationApi } from "@api/fetch/api/v1/workspaces/[workspaceId]/invitations/reissue";
import { startRecordingApi } from "@api/fetch/api/v1/workspaces/[workspaceId]/recordings";
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
  documentConfirmationsResponse,
  documentDetailsResponse,
  documentsResponse,
} from "@api/mock/responses/document";
import {
  recordingAudioUploadCompleteResponse,
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
    describe("GET /api/v1/workspaces/:workspaceId/documents", () => {
      const expected = new GetDocumentsResponseDto(documentsResponse);

      it("documentsResponse를 돌려준다", async () => {
        await expect(
          getDocumentsApi({ workspaceId: WORKSPACE_ID }),
        ).resolves.toEqual(expected);
      });

      it("문서가 size보다 많으면 앞에서 size개만 주고 nextCursor를 준다. 주제 폴더는 전체를 준다", async () => {
        const page = await getDocumentsApi({
          workspaceId: WORKSPACE_ID,
          size: 3,
        });

        expect(page.items).toEqual(expected.items.slice(0, 3));
        expect(page.topics).toEqual(expected.topics);
        expect(page.nextCursor).not.toBeNull();
      });

      it("nextCursor로 요청하면 그다음 문서부터 주고, 마지막 페이지의 nextCursor는 null이다", async () => {
        const { nextCursor } = await getDocumentsApi({
          workspaceId: WORKSPACE_ID,
          size: 4,
        });

        const lastPage = await getDocumentsApi({
          workspaceId: WORKSPACE_ID,
          size: 4,
          cursor: nextCursor ?? undefined,
        });

        expect(lastPage.items).toEqual(expected.items.slice(4));
        expect(lastPage.nextCursor).toBeNull();
      });

      it("없는 cursor면 400 INVALID_PARAMETER로 답한다", async () => {
        await expect(
          getDocumentsApi({ workspaceId: WORKSPACE_ID, cursor: "없는 커서" }),
        ).rejects.toMatchObject({
          type: HTTP_ERROR_TYPE.badRequest,
          code: "INVALID_PARAMETER",
        });
      });

      it("recordingSessionId를 주면 그 녹음에서 나온 문서만 주고, 주제 폴더도 그 문서들만 센다", async () => {
        // 기본 응답에서 같은 녹음에서 나온 문서는 상세 mock이 있는 두 문서예요
        const [{ recordingSessionId }] = documentDetailsResponse;
        const recordingItems = expected.items.filter(
          (item) => item.recordingSessionId === recordingSessionId,
        );

        const page = await getDocumentsApi({
          workspaceId: WORKSPACE_ID,
          recordingSessionId,
        });

        expect(recordingItems).toHaveLength(documentDetailsResponse.length);
        expect(page.items).toEqual(recordingItems);
        expect(page.topics).toEqual(
          // 두 문서의 주제가 서로 달라 주제마다 문서가 하나예요. 순서는 이름순이에요
          recordingItems
            .map(({ topic }) => ({ topic, documentCount: 1 }))
            .sort((a, b) => a.topic.localeCompare(b.topic, "ko")),
        );
        expect(page.nextCursor).toBeNull();
      });

      it("끝까지 받는 getAllDocumentsApi도 recordingSessionId를 주면 그 녹음에서 나온 문서만 받는다", async () => {
        const [{ recordingSessionId }] = documentDetailsResponse;

        const { items, nextCursor } = await getAllDocumentsApi({
          workspaceId: WORKSPACE_ID,
          recordingSessionId,
        });

        expect(items).toEqual(
          expected.items.filter(
            (item) => item.recordingSessionId === recordingSessionId,
          ),
        );
        expect(nextCursor).toBeNull();
      });

      it("recordingSessionId가 1 이상의 정수가 아니면 400 INVALID_PARAMETER로 답한다", async () => {
        await expect(
          getDocumentsApi({ workspaceId: WORKSPACE_ID, recordingSessionId: 0 }),
        ).rejects.toMatchObject({
          type: HTTP_ERROR_TYPE.badRequest,
          code: "INVALID_PARAMETER",
        });
      });

      it("size가 1~100을 벗어나면 400 INVALID_PARAMETER로 답한다", async () => {
        await expect(
          getDocumentsApi({ workspaceId: WORKSPACE_ID, size: 101 }),
        ).rejects.toMatchObject({
          type: HTTP_ERROR_TYPE.badRequest,
          code: "INVALID_PARAMETER",
        });
      });
    });

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
        type: HTTP_ERROR_TYPE.notFound,
        code: "DOCUMENT_NOT_FOUND",
      });
    });

    it("GET /api/v1/workspaces/:workspaceId/documents/:documentId/confirmations는 documentConfirmationsResponse에서 그 문서의 확인 대상을 돌려준다", async () => {
      const [confirmations] = documentConfirmationsResponse;

      await expect(
        getDocumentConfirmationsApi({
          workspaceId: WORKSPACE_ID,
          documentId: confirmations.documentId,
        }),
      ).resolves.toEqual(
        new GetDocumentConfirmationsResponseDto(confirmations),
      );
    });

    it("documentConfirmationsResponse에 없는 문서의 확인 대상을 물으면 404 DOCUMENT_NOT_FOUND로 답한다", async () => {
      await expect(
        getDocumentConfirmationsApi({
          workspaceId: WORKSPACE_ID,
          documentId: 999,
        }),
      ).rejects.toMatchObject({
        type: HTTP_ERROR_TYPE.notFound,
        code: "DOCUMENT_NOT_FOUND",
      });
    });

    describe("PUT /api/v1/workspaces/:workspaceId/documents/:documentId/confirmations/me", () => {
      // 내가 아직 확인하지 않은 문서(101)와 이미 확인한 문서(102)
      const [pendingDocument, confirmedDocument] = documentDetailsResponse;
      const pendingParams = {
        workspaceId: WORKSPACE_ID,
        documentId: pendingDocument.id,
      };

      it("확인하지 않은 문서를 확인하면 확인 수가 1 늘고 미확인 수가 1 준 집계를 돌려준다", async () => {
        const { id, confirmationSummary } = new GetDocumentResponseDto(
          pendingDocument,
        );

        const result = await confirmDocumentApi(pendingParams);

        expect(result).toBeInstanceOf(PutDocumentConfirmationResponseDto);
        expect(result.documentId).toBe(id);
        expect(result.confirmationSummary).toEqual({
          ...confirmationSummary,
          confirmedCount: confirmationSummary.confirmedCount + 1,
          pendingCount: confirmationSummary.pendingCount - 1,
        });
      });

      it("확인한 뒤에는 문서 상세의 내 상태와 집계, 확인 대상의 내 상태가 함께 바뀐다", async () => {
        const { confirmedAt, confirmationSummary } =
          await confirmDocumentApi(pendingParams);

        const document = await getDocumentApi(pendingParams);
        const { items } = await getDocumentConfirmationsApi(pendingParams);
        const myItem = items.find(
          ({ memberId }) => memberId === meResponse.memberId,
        );

        expect(document.myConfirmationState).toBe("CONFIRMED");
        expect(document.confirmationSummary).toEqual(confirmationSummary);
        expect(myItem).toMatchObject({ state: "CONFIRMED", confirmedAt });
        // 서버 정렬처럼 확인한 사람이 미확인인 사람보다 앞에 와요
        expect(items.map(({ state }) => state)).toEqual([
          "CONFIRMED",
          "CONFIRMED",
          "CONFIRMED",
          "PENDING",
        ]);
      });

      it("확인한 뒤에는 문서 목록의 그 문서도 내 상태와 집계가 함께 바뀐다", async () => {
        const { confirmationSummary } = await confirmDocumentApi(pendingParams);

        const { items } = await getDocumentsApi({ workspaceId: WORKSPACE_ID });
        const confirmedItem = items.find(({ id }) => id === pendingDocument.id);

        expect(confirmedItem).toMatchObject({
          myConfirmationState: "CONFIRMED",
          confirmationSummary,
        });
      });

      it("다시 확인해도 처음 확인한 시각과 같은 집계를 돌려준다", async () => {
        const first = await confirmDocumentApi(pendingParams);
        const second = await confirmDocumentApi(pendingParams);

        expect(second).toEqual(first);
      });

      it("이미 확인한 문서를 확인하면 집계를 바꾸지 않고 처음 확인한 시각을 돌려준다", async () => {
        const [, confirmations] = documentConfirmationsResponse;
        const { items } = new GetDocumentConfirmationsResponseDto(
          confirmations,
        );
        const myItem = items.find(
          ({ memberId }) => memberId === meResponse.memberId,
        );
        const expected = new GetDocumentResponseDto(confirmedDocument);

        const result = await confirmDocumentApi({
          workspaceId: WORKSPACE_ID,
          documentId: expected.id,
        });

        expect(result.confirmedAt).toBe(myItem?.confirmedAt);
        expect(result.confirmationSummary).toEqual(
          expected.confirmationSummary,
        );
      });

      it("확인한 기록은 테스트가 끝나면 지워져, 다음 테스트는 기본 응답을 받는다", async () => {
        // 위 테스트들이 101을 확인했지만 vitest.setup의 afterEach가 기록을 지웠어요
        await expect(getDocumentApi(pendingParams)).resolves.toEqual(
          new GetDocumentResponseDto(pendingDocument),
        );
      });

      it("없는 문서를 확인하면 404 DOCUMENT_NOT_FOUND로 답한다", async () => {
        await expect(
          confirmDocumentApi({ workspaceId: WORKSPACE_ID, documentId: 999 }),
        ).rejects.toMatchObject({
          type: HTTP_ERROR_TYPE.notFound,
          code: "DOCUMENT_NOT_FOUND",
        });
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
  });
});
