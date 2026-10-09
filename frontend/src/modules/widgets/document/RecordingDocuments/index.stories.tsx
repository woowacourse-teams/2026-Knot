import { DOCUMENT_GENERATION_JOB_RETRY_API_PATH } from "@api/fetch/api/v1/workspaces/[workspaceId]/documentGenerationJobs/[jobId]/retry";
import { documentGenerationJobRetryResponse } from "@api/mock/responses/documentGenerationJob";
import { recordingDetailsResponse } from "@api/mock/responses/recording";
import { resetRecordingMockState } from "@api/mock/state/recording";
import { getRouterPath, PATH_ROUTE } from "@routes/PATH_ROUTE";
import type { Meta, StoryObj } from "@storybook/react-webpack5";
import { http, HttpResponse } from "msw";
import { MemoryRouter, Route, Routes } from "react-router";

import RecordingDocuments from ".";

const WORKSPACE_ID = 1;
// 상태가 서로 다른 mock 녹음: 정리 중 · 내용 없음 · 문서 만들기 실패 · 전사 실패
const [
  organizingRecording,
  noContentRecording,
  failedRecording,
  transcriptionFailedRecording,
] = recordingDetailsResponse;
// recordingDetailsResponse에 없는 녹음이라 기본 핸들러가 404로 답해요
const MISSING_RECORDING_ID = 999;
// 문서 만들기에 실패한 녹음의 작업을 다시 시도하는 요청이에요.
// 녹음 응답의 작업 ID는 null일 수 있는 타입이라, 같은 작업을 가리키는 다시 시도 응답의 ID를 써요
const RETRY_REQUEST = `*${DOCUMENT_GENERATION_JOB_RETRY_API_PATH(WORKSPACE_ID, documentGenerationJobRetryResponse.jobId)}`;

/**
 * 녹음을 끝낸 뒤 문서가 만들어질 때까지 머무는 정리 화면의 가운데 섹션이에요.
 * 주소의 녹음 상태에 따라 정리 중 · 문서로 만들 내용 없음 · 문서를 만들지 못함 · 불러오지 못함 가운데 하나를 보여 줘요.
 *
 * **동작 규칙**
 * - 정리 중에는 버튼 없이 3초마다 녹음 상태를 다시 확인해요. 결과가 정해지면 그 화면으로 바꾸고 더 확인하지 않아요.
 * - 문서로 만들 내용이 없었으면 오류가 아니라 결과라서 「다시 시도」 없이 「홈으로」만 둬요. 같은 녹음으로는 결과가 같기 때문이에요.
 * - 문서 만들기 단계에서 실패했으면 녹음을 보관했다고 알리고 「다시 시도」를 둬요. 접수되면 정리 중으로 돌아가요.
 *   요청을 기다리는 동안에는 버튼을 누를 수 없고, 서버 오류로 실패하면 버튼이 다시 열려요.
 * - 전사 · 오디오 업로드 실패, 다시 시도할 작업이 없는 실패, 다시 시도가 거절된 경우는 설명 없이 「홈으로」만 둬요.
 *   이 경우의 문구가 기획에 아직 없고, 녹음이 보관되어 있는지도 경우마다 달라서예요.
 * - 다시 시도가 409로 거절되면 녹음 상태를 다시 확인해요. 다른 곳에서 먼저 다시 시도해 서버에서는 이미 정리 중이거나 정리가 끝났을 수 있어서예요.
 *   그런 경우에는 그 상태의 화면으로 바꾸고, 그 작업이 또 실패하면 「다시 시도」를 다시 보여 줘요.
 * - 녹음 상태를 불러오지 못하면 「문서를 불러오지 못했어요」와 「다시 시도」를 보여 줘요.
 *   이때는 저절로 다시 확인하지 않고, 「다시 시도」를 누르면 다시 확인해요.
 * - 정리 중에 녹음이 없어지거나 볼 수 없게 되어도 「문서를 불러오지 못했어요」로 바꾸고 더 확인하지 않아요.
 *   서버 · 네트워크 문제로 확인에 실패한 것이면 정리 중 화면을 유지하고 계속 확인해요.
 * - 주소의 번호가 정수가 아니면 요청하지 않고 「문서를 불러오지 못했어요」를 보여 줘요. 이때 「다시 시도」는 아무 일도 하지 않아요.
 * - 정리가 끝난 녹음은 워크스페이스 홈으로, 아직 녹음 중이거나 일시 정지한 녹음은 녹음 화면으로, 로그인이 풀렸으면 로그인 화면으로 보내요.
 *   정리가 끝난 녹음은 녹음 직후 확인 화면이 생기면 그 화면으로 보낼 예정이에요.
 */
const meta = {
  title: "Document/RecordingDocuments",
  component: RecordingDocuments,
  parameters: {
    recordingId: organizingRecording.recordingId,
    design: {
      type: "figma",
      url: "https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=2017-35460",
    },
  },
  // 다시 시도한 기록은 mock 상태에 남으므로, 스토리마다 기본 응답으로 되돌려요
  loaders: [
    () => {
      resetRecordingMockState();
    },
  ],
  decorators: [
    (Story, { parameters }) => (
      <MemoryRouter
        initialEntries={[
          getRouterPath({
            routeKey: "RECORDING_DOCUMENTS",
            params: {
              workspaceId: String(WORKSPACE_ID),
              recordingId: String(parameters.recordingId),
            },
          }),
        ]}
      >
        <Routes>
          <Route path={PATH_ROUTE.RECORDING_DOCUMENTS} element={<Story />} />
          <Route
            path={PATH_ROUTE.WORKSPACE_HOME}
            element={<p>워크스페이스 홈으로 이동했어요.</p>}
          />
        </Routes>
      </MemoryRouter>
    ),
  ],
} satisfies Meta<typeof RecordingDocuments>;

export default meta;

type Story = StoryObj<typeof meta>;

/** 녹음을 끝내고 문서를 정리하는 동안의 모습이에요. 버튼 없이 기다리게 하고, 3초마다 상태를 다시 확인해요. */
export const Default: Story = {};

/** 대화가 너무 짧거나 정리할 논의가 없어 문서를 만들지 않았을 때예요. 오류가 아니라 결과라서 「다시 시도」 없이 「홈으로」만 있어요. */
export const NothingToOrganize: Story = {
  parameters: {
    recordingId: noContentRecording.recordingId,
    design: {
      type: "figma",
      url: "https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=2262-33302",
    },
  },
};

/** 문서 만들기 단계에서 실패했을 때예요. 녹음은 보관돼 있어 「다시 시도」를 둬요. 누르면 정리 중 모습으로 돌아가요. */
export const DocumentFailed: Story = {
  parameters: {
    recordingId: failedRecording.recordingId,
    design: {
      type: "figma",
      url: "https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=2017-35662",
    },
  },
};

/** 다시 시도해도 결과가 같은 실패일 때예요. 예: 전사 실패, 오디오 업로드 실패. 설명 없이 「홈으로」만 있어요. 붙인 피그마 프레임은 「다시 시도」가 있는 모습이에요. */
export const DocumentFailedWithoutRetry: Story = {
  parameters: {
    recordingId: transcriptionFailedRecording.recordingId,
    design: {
      type: "figma",
      url: "https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=2017-35662",
    },
  },
};

/** 「다시 시도」를 서버가 거절했고, 다시 확인한 녹음도 여전히 실패 상태일 때예요. 예: 다시 시도할 수 있는 횟수를 다 썼거나 기한이 지난 작업. 누르면 설명과 버튼이 사라지고 「홈으로」만 남아요. */
export const RetryRejected: Story = {
  parameters: {
    // 문서 페이지에서는 스토리를 한 화면에 함께 그려 응답이 섞이므로, 이 스토리는 따로 그려요
    docs: { story: { inline: false, iframeHeight: 320 } },
    recordingId: failedRecording.recordingId,
    design: {
      type: "figma",
      url: "https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=2017-35662",
    },
    msw: {
      handlers: {
        retryDocumentGenerationJob: http.post(
          RETRY_REQUEST,
          () => new HttpResponse(null, { status: 409 }),
        ),
      },
    },
  },
};

/** 녹음 상태를 불러오지 못했을 때예요. 없는 녹음, 볼 수 없는 녹음, 서버 오류, 네트워크 문제가 모두 이 모습이에요. 이 스토리에서는 「다시 시도」를 눌러도 계속 실패해요. */
export const LoadFailed: Story = {
  parameters: {
    recordingId: MISSING_RECORDING_ID,
    design: {
      type: "figma",
      url: "https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=3669-13842",
    },
  },
};
