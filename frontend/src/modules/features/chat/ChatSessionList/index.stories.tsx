import { chatSessionsResponse } from "@api/mock/responses/chatSession";
import { PATH_ROUTE, getRouterPath } from "@routes/PATH_ROUTE";
import type { Meta, StoryObj } from "@storybook/react-webpack5";
import { http, HttpResponse } from "msw";
import { MemoryRouter, Route, Routes } from "react-router";

import ChatSessionList from ".";

const WORKSPACE_ID = "1";
const SESSION_PATH = getRouterPath({
  routeKey: "CHAT_SESSION",
  params: {
    workspaceId: WORKSPACE_ID,
    sessionId: String(chatSessionsResponse[0].id),
  },
});
const CONVERSATIONS_URL = "*/api/v1/workspaces/:workspaceId/conversations";

const DAY = 24 * 60 * 60 * 1000;
const fromNow = (elapsed: number) =>
  new Date(Date.now() - elapsed).toISOString();

/**
 * 워크스페이스에 쌓인 대화 목록이에요. 탐색 화면의 대화 목록 드로어(`Chat/ChatListDrawer`) 안에 놓여요.
 *
 * **동작 규칙**
 * - 대화를 마지막 메시지 시각으로 「오늘 / 이번 주 / 지난 30일 / 이전」에 묶어요. 오늘은 날짜가 같은지로, 나머지는 지금으로부터 지난 시간으로 가르고, 대화가 없는 묶음은 감춰요.
 * - 같은 묶음 안에서는 마지막 메시지가 최근인 대화가 위로 와요.
 * - 한 줄에는 대화 제목과 마지막으로 오간 시각을 보여 주고, 제목이 길면 한 줄에서 말줄임표로 잘라요.
 * - 지금 보고 있는 대화(주소의 대화 ID)는 채워진 모양으로 표시하고, 화면 낭독기에도 지금 보고 있는 대화라고 알려요. 다른 대화를 누르면 그 대화 화면으로 옮겨 가요.
 * - 대화가 하나도 없으면 목록 자리에 빈 안내를 띄워요. 대화가 없는 게 오류가 아니라는 것과, 새 대화를 시작하면 목록이 채워진다는 것을 알려 줘요. 목록을 받아 오는 중이거나 받아 오지 못했을 때도 같은 안내가 보여요.
 */
const meta = {
  title: "Chat/ChatSessionList",
  component: ChatSessionList,
  parameters: {
    layout: "padded",
    design: [
      {
        name: "대화 목록",
        type: "figma",
        url: "https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=580-1961",
      },
      {
        name: "기간 묶음",
        type: "figma",
        url: "https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=580-1533",
      },
      {
        name: "SessionRow",
        type: "figma",
        url: "https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=1379-8248",
      },
      {
        name: "빈 목록",
        type: "figma",
        url: "https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=1254-5857",
      },
    ],
  },
  decorators: [
    (Story) => (
      <MemoryRouter initialEntries={[SESSION_PATH]}>
        <Routes>
          {[PATH_ROUTE.CHAT, PATH_ROUTE.CHAT_SESSION].map((path) => (
            <Route
              key={path}
              path={path}
              element={
                // 대화 목록 드로어의 내용 너비(280px - 좌우 여백 32px)와 높이를 흉내 내요
                <div style={{ width: "15.5rem", height: "30rem" }}>
                  <Story />
                </div>
              }
            />
          ))}
        </Routes>
      </MemoryRouter>
    ),
  ],
} satisfies Meta<typeof ChatSessionList>;

export default meta;

type Story = StoryObj<typeof meta>;

/** 대화 하나를 보고 있는 탐색 화면이에요. 보고 있는 대화가 채워진 모양으로 표시돼요. */
export const Default: Story = {};

/** 대화가 여러 기간에 걸쳐 쌓인 워크스페이스예요. 묶음이 최근 기간부터 나오고, 긴 제목은 말줄임표로 잘려요. */
export const AllPeriods: Story = {
  parameters: {
    // 문서 페이지에서는 스토리를 한 화면에 함께 그려 응답이 섞이므로, 이 스토리는 따로 그려요
    docs: { story: { inline: false, iframeHeight: 520 } },
    msw: {
      handlers: {
        conversations: http.get(CONVERSATIONS_URL, () =>
          HttpResponse.json([
            ...chatSessionsResponse,
            {
              id: 103,
              title: "스프린트 3 회고에서 나온 배포 자동화 개선 아이디어 정리",
              createdAt: fromNow(12 * DAY),
              lastMessageAt: fromNow(10 * DAY),
            },
            {
              id: 104,
              title: "API 명세 리뷰",
              createdAt: fromNow(50 * DAY),
              lastMessageAt: fromNow(45 * DAY),
            },
          ]),
        ),
      },
    },
  },
};
