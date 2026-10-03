import { chatSessionsResponse } from "@api/mock/responses/chatSession";
import { PATH_ROUTE, getRouterPath } from "@routes/PATH_ROUTE";
import type { Meta, StoryObj } from "@storybook/react-webpack5";
import { http, HttpResponse } from "msw";
import { MemoryRouter, Route, Routes } from "react-router";

import ChatListDrawer from ".";

const WORKSPACE_ID = "1";
const SESSION_PATH = getRouterPath({
  routeKey: "CHAT_SESSION",
  params: {
    workspaceId: WORKSPACE_ID,
    sessionId: String(chatSessionsResponse[0].id),
  },
});

/**
 * 탐색 화면의 대화 목록 드로어예요. GNB 왼쪽의 목록 버튼으로 여닫으며, 탐색 화면에서만 열 수 있어요.
 *
 * **동작 규칙**
 * - 버튼에 마우스를 올리면 화면 위에 겹쳐 뜨고, 누르면 왼쪽에 자리를 잡아요. 이 여닫기는 감싸는 `Shared/DockablePanel`이 맡아요.
 * - 목록은 `Chat/ChatSessionList`가 그대로 그리고, 드로어는 제목과 「새 채팅」 버튼만 얹어요.
 * - 「새 채팅」을 누르면 고른 대화 없이 탐색 화면으로 옮겨 가 새 대화를 시작해요.
 *
 * **디자인 원본**: [탐색 결과/채팅 세션 목록](https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=526-772) ·
 * [DrawerHead](https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=1379-8238) ·
 * [Btn/새채팅](https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=1379-8240)
 */
const meta = {
  title: "Chat/ChatListDrawer",
  component: ChatListDrawer,
  parameters: { layout: "padded" },
  decorators: [
    (Story) => (
      <MemoryRouter initialEntries={[SESSION_PATH]}>
        <Routes>
          {[PATH_ROUTE.CHAT, PATH_ROUTE.CHAT_SESSION].map((path) => (
            <Route
              key={path}
              path={path}
              element={
                // 드로어는 화면 높이를 채우므로 탐색 화면과 비슷한 높이를 줘요
                <div style={{ height: "35rem" }}>
                  <Story />
                </div>
              }
            />
          ))}
        </Routes>
      </MemoryRouter>
    ),
  ],
} satisfies Meta<typeof ChatListDrawer>;

export default meta;

type Story = StoryObj<typeof meta>;

/** 대화 하나를 보고 있는 탐색 화면에서 연 드로어예요. */
export const Default: Story = {};

/** 아직 나눈 대화가 없는 워크스페이스에서 연 드로어예요. 예: 워크스페이스를 막 만든 직후 */
export const Empty: Story = {
  parameters: {
    msw: {
      handlers: {
        conversations: http.get(
          "*/api/v1/workspaces/:workspaceId/conversations",
          () => HttpResponse.json([]),
        ),
      },
    },
  },
};
