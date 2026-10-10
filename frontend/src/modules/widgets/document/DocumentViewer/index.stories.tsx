import { DOCUMENT_API_PATH } from "@api/fetch/api/v1/workspaces/[workspaceId]/documents/[documentId]";
import { documentDetailsResponse } from "@api/mock/responses/document";
import { resetDocumentMockState } from "@api/mock/state/document";
import { getRouterPath, PATH_ROUTE } from "@routes/PATH_ROUTE";
import type { Meta, StoryObj } from "@storybook/react-webpack5";
import { delay, http, HttpResponse } from "msw";
import { MemoryRouter, Route, Routes } from "react-router";

import DocumentViewer from ".";

const WORKSPACE_ID = 1;
const [documentResponse, confirmedDocumentResponse] = documentDetailsResponse;
const DOCUMENT_REQUEST = `*${DOCUMENT_API_PATH(WORKSPACE_ID, documentResponse.id)}`;

/** 이 워크스페이스에서 문서를 여는 주소예요. */
const getDocumentPath = (documentId: number) =>
  getRouterPath({
    routeKey: "DOCUMENT",
    params: {
      workspaceId: String(WORKSPACE_ID),
      documentId: String(documentId),
    },
  });

// 문서 페이지는 스토리를 한 화면에 함께 그리는데 msw는 화면에 하나뿐이라, 마지막 스토리의 응답이 모든 스토리에 적용돼요.
// 응답을 바꾸는 스토리만 따로 그려 각자의 응답을 받게 해요
const ISOLATED_DOCS = { story: { inline: false, iframeHeight: 260 } };

/**
 * 문서 하나를 열었을 때 경로 · 복사 버튼 · 제목 · 날짜 · 녹음 길이 · 확인 수 · 본문 · 확인 버튼을 보여 주는 문서 보기 영역이에요.
 * 탐색의 찾은 기록 카드, 문서 목록, 사이드바, 홈 카드에서 문서를 누르면 이 화면이 열려요.
 *
 * **동작 규칙**
 * - 주소의 문서 번호로 문서를 불러와요. 번호가 숫자인지는 확인하지 않고 그대로 요청하고, 서버가 거절하면 「문서를 불러오지 못했어요」를 보여 줘요.
 * - 없는 문서이거나 볼 수 없는 문서일 때, 서버 오류나 네트워크 문제로 못 불러올 때도 「문서를 불러오지 못했어요」와 `다시 시도`를 보여 줘요.
 *   `다시 시도`를 누르면 화면을 새로고침하지 않고 문서만 다시 불러와요.
 * - 로그인이 풀렸으면 로그인 화면으로 보내요.
 * - 결정이 없는 회의 문서는 「이번 회의에서 정해진 내용은 없어요.」 문장을 흐리게 보여 줘요.
 * - 확인 수에 포인터를 올리면 확인한 사람이 위에, 아직 확인하지 않은 사람이 아래에 흐리게 보여요. 확인하지 않고 나간 사람은 빼요.
 * - 「복사」를 누르면 제목과 본문을 마크다운으로 복사하고 3초 동안 「복사됨」으로 바뀌어요.
 * - 「문서를 확인했어요」는 아직 확인하지 않은 확인 대상에게만 보이고, 누르면 사라져요. 실패하면 다시 누를 수 있어요.
 */
const meta = {
  title: "Document/DocumentViewer",
  component: DocumentViewer,
  parameters: {
    documentId: documentResponse.id,
    design: {
      type: "figma",
      url: "https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=2017-36058",
    },
  },
  // 「문서를 확인했어요」를 누른 기록이 다른 스토리에 남지 않게, 스토리를 열 때마다 mock 상태를 처음으로 되돌려요
  loaders: [
    () => {
      resetDocumentMockState();
    },
  ],
  decorators: [
    (Story, { parameters }) => (
      <MemoryRouter initialEntries={[getDocumentPath(parameters.documentId)]}>
        <Routes>
          <Route path={PATH_ROUTE.DOCUMENT} element={<Story />} />
        </Routes>
      </MemoryRouter>
    ),
  ],
} satisfies Meta<typeof DocumentViewer>;

export default meta;

type Story = StoryObj<typeof meta>;

/**
 * 문서를 불러온 기본 모습이에요. 경로 · 복사 버튼 · 제목 · 날짜 · 녹음 길이 · 확인 수 · 본문 · 확인 버튼이 놓여요.
 * 확인 수에 포인터를 올리면 확인한 사람이 뜨고, 「문서를 확인했어요」를 누르면 버튼이 사라지고 확인 수가 「3명 확인했어요」로 늘어나요.
 */
export const Default: Story = {};

/**
 * 이미 확인한 문서를 열었을 때예요. 「문서를 확인했어요」 버튼과 그 줄이 없어요.
 * Default와 다른 문서(결정이 없는 회의 문서)를 열어 본문도 달라요. 본문의 「이번 회의에서 정해진 내용은 없어요.」 문장이 흐리게 보여요.
 */
export const AlreadyConfirmed: Story = {
  parameters: { documentId: confirmedDocumentResponse.id },
};

/** 문서를 불러오는 동안 보이는 모습이에요. 경로 · 복사 버튼 · 제목 · 날짜 줄 · 본문 자리를 회색 덩어리로 채워 둬요. */
export const Loading: Story = {
  parameters: {
    // 회색 덩어리가 다른 스토리의 안내보다 길어 높이를 따로 둬요
    docs: { story: { inline: false, iframeHeight: 360 } },
    msw: {
      handlers: {
        document: http.get(DOCUMENT_REQUEST, async () => {
          await delay("infinite");
          return HttpResponse.json(documentResponse);
        }),
      },
    },
  },
};

/** 문서를 불러오지 못했을 때예요. 없는 문서 · 볼 수 없는 문서 · 서버 오류 · 네트워크 문제가 모두 이 모습이에요. 이 스토리에서는 `다시 시도`를 눌러도 계속 실패해요. */
export const LoadFailed: Story = {
  parameters: {
    design: {
      type: "figma",
      url: "https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=3669-13842",
    },
    docs: ISOLATED_DOCS,
    msw: {
      handlers: {
        document: http.get(
          DOCUMENT_REQUEST,
          () => new HttpResponse(null, { status: 500 }),
        ),
      },
    },
  },
};
