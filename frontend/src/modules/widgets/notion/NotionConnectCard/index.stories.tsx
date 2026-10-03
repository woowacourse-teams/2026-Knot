import { NOTION_OAUTH_AUTHORIZATIONS_API_PATH } from "@api/fetch/api/v1/workspaces/[workspaceId]/notionOauthAuthorizations";
import { notionOAuthAuthorizationResponse } from "@api/mock/responses/notionConnection";
import { PATH_ROUTE, getRouterPath } from "@routes/PATH_ROUTE";
import type { Meta, StoryObj } from "@storybook/react-webpack5";
import { delay, http, HttpResponse } from "msw";
import { MemoryRouter, Route, Routes } from "react-router";
import { userEvent, within } from "storybook/test";

import NotionConnectCard from ".";

const WORKSPACE_ID = 1;
const NOTION_CONNECTION_PATH = getRouterPath({
  routeKey: "WORKSPACE_NOTION_CONNECTION",
  params: { workspaceId: String(WORKSPACE_ID) },
});
const START_URL = `*${NOTION_OAUTH_AUTHORIZATIONS_API_PATH(WORKSPACE_ID)}`;

/** 연결 시작 요청이 이 상태 코드로 실패하게 해요. */
const failStartWith = (status: number) =>
  http.post(START_URL, () => new HttpResponse(null, { status }));

/** 「노션 연결하기」를 눌러요. */
const clickConnect = async (canvasElement: HTMLElement) => {
  const canvas = within(canvasElement);

  await userEvent.click(canvas.getByRole("button", { name: "노션 연결하기" }));
};

/**
 * 노션 연동 카드예요. 워크스페이스 생성 플로우의 마지막 단계(`/workspace/:workspaceId/notion-connection`)에서 노션에 쌓아 둔 기록을 knot로 옮길지 물어요.
 *
 * **동작 규칙**
 * - 「노션 연결하기」를 누르면 Notion 인증 페이지로 페이지를 통째로 옮겨요. 옮겨 갈 때까지 버튼을 로딩으로 붙잡아 두 번 눌리지 않게 해요.
 * - Notion에서 돌아올 때 연결에 성공했으면 워크스페이스 홈으로 보내고, 거절·취소했으면 실패 카드를 보여 줘요. 실패 카드에서는 연결 없이 워크스페이스로 이동할 수 있어요.
 * - 돌아온 주소의 결과 값을 알 수 없으면 잘못된 접근으로 보고 처음 연결 카드로 되돌려요.
 * - 연결을 시작하지 못하면 버튼 아래에 이유를 띄워요. 로그인이 풀렸으면 로그인 화면으로, 없는 워크스페이스면 워크스페이스 선택 화면으로 대신 보내요.
 * - 「워크스페이스로 이동」은 연결 없이 워크스페이스 홈으로 가요.
 * - 로고와 화면 가운데 배치는 화면 레이아웃이 맡아요. 이 카드는 카드 모양만 그려요.
 *
 * 스토리북에서는 실제 Notion으로 넘어가지 않도록 연결 시작 응답을 붙잡아 둬요. 「노션 연결하기」를 누르면 이동 직전처럼 버튼이 로딩에 머물러요.
 *
 * **디자인 원본**: [새 워크스페이스 생성/노션에서 가져오기](https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=432-1946) ·
 * [Card/Onboarding & Workspace](https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=432-1949) ·
 * [Icon/Notion size=48](https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=542-261)
 */
const meta = {
  title: "Notion/NotionConnectCard",
  component: NotionConnectCard,
  parameters: {
    layout: "centered",
    search: "",
    msw: {
      handlers: {
        startNotionOAuth: http.post(START_URL, async () => {
          await delay("infinite");
          return HttpResponse.json(notionOAuthAuthorizationResponse, {
            status: 201,
          });
        }),
      },
    },
  },
  decorators: [
    (Story, { parameters }) => (
      <MemoryRouter
        initialEntries={[`${NOTION_CONNECTION_PATH}${parameters.search}`]}
      >
        <Routes>
          <Route
            path={PATH_ROUTE.WORKSPACE_NOTION_CONNECTION}
            element={
              // 카드가 최대 너비까지 펼쳐지도록 자리를 줘요
              <div style={{ width: "28.75rem" }}>
                <Story />
              </div>
            }
          />
          <Route path="*" element={<p>다음 화면으로 이동했어요.</p>} />
        </Routes>
      </MemoryRouter>
    ),
  ],
} satisfies Meta<typeof NotionConnectCard>;

export default meta;

type Story = StoryObj<typeof meta>;

/** 워크스페이스를 막 만든 사용자가 처음 보는 연결 카드예요. */
export const Default: Story = {};

/** 「노션 연결하기」를 눌러 Notion 인증 페이지로 옮겨 가는 중이에요. */
export const Connecting: Story = {
  play: async ({ canvasElement }) => {
    await clickConnect(canvasElement);
  },
};

/** 워크스페이스 소유자가 아닌 멤버가 연결하려 할 때예요. 소유자만 연결할 수 있다고 알려요. */
export const ConnectForbidden: Story = {
  parameters: {
    msw: { handlers: { startNotionOAuth: failStartWith(403) } },
  },
  play: async ({ canvasElement }) => {
    await clickConnect(canvasElement);
  },
};

/** 서버 오류·네트워크 문제로 연결을 시작하지 못했을 때예요. 잠시 후 다시 시도하도록 안내해요. */
export const ConnectFailed: Story = {
  parameters: {
    msw: { handlers: { startNotionOAuth: failStartWith(500) } },
  },
  play: async ({ canvasElement }) => {
    await clickConnect(canvasElement);
  },
};

/** Notion 인증 페이지에서 거절·취소하고 돌아왔을 때예요. 연결 없이 워크스페이스로 이동하게 해요. */
export const ReturnedFailed: Story = {
  parameters: { search: "?result=failed" },
};
