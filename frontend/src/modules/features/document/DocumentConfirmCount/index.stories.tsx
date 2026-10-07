import { DOCUMENT_CONFIRMATIONS_API_PATH } from "@api/fetch/api/v1/workspaces/[workspaceId]/documents/[documentId]/confirmations";
import { documentDetailsResponse } from "@api/mock/responses/document";
import { resetDocumentMockState } from "@api/mock/state/document";
import { getRouterPath, PATH_ROUTE } from "@routes/PATH_ROUTE";
import type { Meta, StoryObj } from "@storybook/react-webpack5";
import { http, HttpResponse } from "msw";
import { MemoryRouter, Route, Routes } from "react-router";

import DocumentConfirmCount from ".";

const WORKSPACE_ID = 1;
const [documentResponse] = documentDetailsResponse;
const DOCUMENT_PATH = getRouterPath({
  routeKey: "DOCUMENT",
  params: {
    workspaceId: String(WORKSPACE_ID),
    documentId: String(documentResponse.id),
  },
});
const CONFIRMATIONS_REQUEST = `*${DOCUMENT_CONFIRMATIONS_API_PATH(WORKSPACE_ID, documentResponse.id)}`;

/**
 * 문서를 확인한 사람 수예요. 문서 보기에서 제목 아래 줄의 오른쪽 끝에 「2명 확인했어요」처럼 놓여요.
 * 확인 수에 포인터를 올리면 누가 확인했고 누가 아직인지 팝오버로 보여 줘요.
 *
 * **동작 규칙**
 * - 수는 문서와 함께 받은 확인 집계를 써요. 문서를 아직 받지 못했으면 아무것도 그리지 않아요.
 * - 팝오버에는 확인한 사람을 위에, 아직 확인하지 않은 사람을 아래에 흐리게 보여 줘요. 서버가 보낸 순서와 관계없이 이렇게 나눠요.
 * - 확인하지 않은 채 워크스페이스를 나간 사람은 팝오버에 넣지 않아요.
 * - 「n명 중 m명」 같은 머리글과 확인한 시각은 두지 않아요.
 * - 확인 대상 목록은 팝오버를 열 때가 아니라 확인 수가 그려질 때 불러와요. 포인터를 올리자마자 열리는 팝오버라, 그때 불러오면 기다리는 모습이 눈에 띄어서예요.
 * - 목록을 불러오지 못하면 팝오버에 「목록을 불러오지 못했어요」를 보여 주고, 포인터를 다시 올리면 다시 불러와요.
 * - 포인터로만 열려요. 키보드로 여는 동작은 아직 없어요.
 */
const meta = {
  title: "Document/DocumentConfirmCount",
  component: DocumentConfirmCount,
  parameters: {
    layout: "centered",
    design: [
      {
        name: "문서/확인한 사람 보기",
        type: "figma",
        url: "https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=2017-36058",
      },
      {
        name: "Popover/확인한 사람",
        type: "figma",
        url: "https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=1909-5316",
      },
    ],
  },
  // 확인 버튼 스토리에서 누른 확인이 mock에 남아 3명으로 보일 수 있어, 열 때마다 처음 상태로 되돌려요
  loaders: [
    () => {
      resetDocumentMockState();
    },
  ],
  decorators: [
    (Story) => (
      <MemoryRouter initialEntries={[DOCUMENT_PATH]}>
        <Routes>
          <Route path={PATH_ROUTE.DOCUMENT} element={<Story />} />
        </Routes>
      </MemoryRouter>
    ),
  ],
  args: { documentId: documentResponse.id },
} satisfies Meta<typeof DocumentConfirmCount>;

export default meta;

type Story = StoryObj<typeof meta>;

/** 문서를 연 기본 모습이에요. 「2명 확인했어요」가 보이고, 확인 수에 포인터를 올리면 확인한 사람이 위에, 아직 확인하지 않은 사람이 아래에 흐리게 떠요. */
export const Default: Story = {};

/**
 * 확인 대상 목록을 불러오지 못했을 때예요. 확인 수는 그대로 보이고, 확인 수에 포인터를 올리면 「목록을 불러오지 못했어요」가 떠요.
 * 포인터를 다시 올리면 다시 불러와요. 이 스토리는 서버가 계속 실패하게 해 두어 다시 올려도 같은 알림이 떠요.
 */
export const PeopleLoadFailed: Story = {
  parameters: {
    // 문서 페이지에서는 스토리를 한 화면에 함께 그려 응답이 섞이므로, 이 스토리는 따로 그려요
    docs: { story: { inline: false, iframeHeight: 160 } },
    msw: {
      handlers: {
        documentConfirmations: http.get(
          CONFIRMATIONS_REQUEST,
          () => new HttpResponse(null, { status: 500 }),
        ),
      },
    },
  },
};
