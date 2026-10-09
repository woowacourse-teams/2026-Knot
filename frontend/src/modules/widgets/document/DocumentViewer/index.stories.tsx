import { DOCUMENT_API_PATH } from "@api/fetch/api/v1/workspaces/[workspaceId]/documents/[documentId]";
import { documentDetailsResponse } from "@api/mock/responses/document";
import { getRouterPath, PATH_ROUTE } from "@routes/PATH_ROUTE";
import type { Meta, StoryObj } from "@storybook/react-webpack5";
import { delay, http, HttpResponse } from "msw";
import { MemoryRouter, Route, Routes } from "react-router";

import DocumentViewer from ".";

const WORKSPACE_ID = 1;
const [documentResponse] = documentDetailsResponse;
const DOCUMENT_PATH = getRouterPath({
  routeKey: "DOCUMENT",
  params: {
    workspaceId: String(WORKSPACE_ID),
    documentId: String(documentResponse.id),
  },
});
const DOCUMENT_REQUEST = `*${DOCUMENT_API_PATH(WORKSPACE_ID, documentResponse.id)}`;

// 문서 페이지는 스토리를 한 화면에 함께 그리는데 msw는 화면에 하나뿐이라, 마지막 스토리의 응답이 모든 스토리에 적용돼요.
// 응답을 바꾸는 스토리만 따로 그려 각자의 응답을 받게 해요
const ISOLATED_DOCS = { story: { inline: false, iframeHeight: 260 } };

/**
 * 문서 하나를 열었을 때 제목과 본문을 보여 주는 문서 보기 영역이에요.
 * 탐색의 찾은 기록 카드, 문서 목록, 사이드바, 홈 카드에서 문서를 누르면 이 화면이 열려요.
 *
 * **동작 규칙**
 * - 주소의 문서 번호로 문서를 불러와요. 번호가 숫자가 아니면 요청하지 않고 「문서를 찾을 수 없어요」를 보여 줘요.
 * - 없는 문서이거나 볼 수 없는 문서면 「문서를 찾을 수 없어요」와 `홈으로`를 보여 줘요.
 *   기획은 공통 「잘못된 요청」 화면으로 보내는 것이라, 그 화면이 생기면 바꿔요.
 * - 서버 오류나 네트워크 문제로 못 불러오면 `다시 시도`를 보여 줘요. 이것도 공통 오류 화면이 생기기 전까지의 임시 모습이에요.
 * - 로그인이 풀렸으면 로그인 화면으로 보내요.
 * - 결정이 없는 회의 문서는 「이번 회의에서 정해진 내용은 없어요.」 문장을 흐리게 보여 줘요.
 * - 경로 · 날짜 · 확인 수 · 복사 버튼과 확인 버튼은 아직 없고 다음 작업에서 더해요.
 */
const meta = {
  title: "Document/DocumentViewer",
  component: DocumentViewer,
  parameters: {
    design: {
      type: "figma",
      url: "https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=2017-36058",
    },
  },
  decorators: [
    (Story) => (
      <MemoryRouter initialEntries={[DOCUMENT_PATH]}>
        <Routes>
          <Route path={PATH_ROUTE.DOCUMENT} element={<Story />} />
          <Route
            path={PATH_ROUTE.WORKSPACE_HOME}
            element={<p>워크스페이스 홈으로 이동했어요.</p>}
          />
        </Routes>
      </MemoryRouter>
    ),
  ],
} satisfies Meta<typeof DocumentViewer>;

export default meta;

type Story = StoryObj<typeof meta>;

/** 문서를 불러온 기본 모습이에요. 제목 아래에 본문이 놓여요. */
export const Default: Story = {};

/** 문서를 불러오는 동안 보이는 모습이에요. 제목과 본문 자리를 회색 덩어리로 채워 둬요. */
export const Loading: Story = {
  parameters: {
    docs: ISOLATED_DOCS,
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

/** 없는 문서이거나 볼 수 없는 문서일 때예요. `홈으로`를 누르면 워크스페이스 홈으로 가요. */
export const NotFound: Story = {
  parameters: {
    docs: ISOLATED_DOCS,
    msw: {
      handlers: {
        document: http.get(
          DOCUMENT_REQUEST,
          () => new HttpResponse(null, { status: 404 }),
        ),
      },
    },
  },
};

/** 서버 오류나 네트워크 문제로 문서를 불러오지 못했을 때예요. `다시 시도`를 누르면 다시 불러와요. */
export const LoadFailed: Story = {
  parameters: {
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
