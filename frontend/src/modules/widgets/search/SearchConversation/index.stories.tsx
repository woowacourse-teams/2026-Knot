import { PATH_ROUTE, getRouterPath } from "@routes/PATH_ROUTE";
import type { Meta, StoryObj } from "@storybook/react-webpack5";
import { MemoryRouter, Route, Routes } from "react-router";

import SearchConversation from ".";

const WORKSPACE_ID = "1";
const CHAT_PATH = getRouterPath({
  routeKey: "CHAT",
  params: { workspaceId: WORKSPACE_ID },
});
const CONVERSATION_PATH = getRouterPath({
  routeKey: "CHAT_SESSION",
  params: { workspaceId: WORKSPACE_ID, sessionId: "10" },
});
/** 예시 대화에서 근거가 2개인 첫 답변의 ID */
const FIRST_ANSWER_ID = 2;

/**
 * 탐색 화면 가운데의 대화 칸이에요. 지금 대화가 어떤 상태인지에 따라 칸에 무엇을 그릴지 정해요.
 *
 * **동작 규칙**
 * - 질문이 아직 없는 빈 대화에서는 「무엇을 찾고 있나요?」 안내를 칸 가운데에 띄워요.
 * - 빈 대화는 저장하지 않고, 첫 질문을 받은 뒤에야 대화가 돼요.
 * - 대화가 있으면 질문은 오른쪽 말풍선, 답변은 왼쪽 문단으로 위에서부터 차례로 그려요. 답변은 줄바꿈마다 문단을 나눠요.
 * - 근거가 있는 답변 아래에만 「기록 N개에서 찾았어요」 버튼이 있어요. 누르면 그 답변의 찾은 기록이 화면 오른쪽 패널에 열려요.
 * - 아직 메시지 조회 API를 연결하지 않아 예시 대화 하나를 그려요. 근거가 2개·3개·없는 답변이 한 턴씩 있어요.
 */
const meta = {
  title: "Search/SearchConversation",
  component: SearchConversation,
  parameters: {
    layout: "fullscreen",
    initialPath: CHAT_PATH,
    design: [
      {
        name: "탐색/시작 전",
        type: "figma",
        url: "https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=2098-29235",
      },
      {
        name: "Search/Answer",
        type: "figma",
        url: "https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=2017-36200",
      },
    ],
  },
  decorators: [
    (Story, { parameters }) => (
      <MemoryRouter initialEntries={[parameters.initialPath]}>
        <Routes>
          {[PATH_ROUTE.CHAT, PATH_ROUTE.CHAT_SESSION].map((path) => (
            <Route
              key={path}
              path={path}
              element={
                // 대화 칸은 화면 높이를 채우고 독과 같은 폭(760px)에 놓이므로 탐색 화면과 비슷하게 줘요
                <div
                  style={{
                    height: "40rem",
                    maxWidth: "47.5rem",
                    margin: "0 auto",
                  }}
                >
                  <Story />
                </div>
              }
            />
          ))}
        </Routes>
      </MemoryRouter>
    ),
  ],
} satisfies Meta<typeof SearchConversation>;

export default meta;

type Story = StoryObj<typeof meta>;

/** 새 채팅을 눌러 질문 없이 탐색 화면에 들어온 상태예요. */
export const Default: Story = {};

/** 질문과 답변이 오간 대화예요. 찾은 기록은 닫혀 있어요. */
export const Conversation: Story = {
  parameters: {
    initialPath: CONVERSATION_PATH,
    design: {
      type: "figma",
      url: "https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=2106-28969",
    },
  },
};

/** 첫 답변의 「기록 2개에서 찾았어요」를 눌러 그 답변의 찾은 기록을 연 상태예요. 그 버튼만 채워진 모양이에요. */
export const EvidenceOpen: Story = {
  parameters: {
    initialPath: `${CONVERSATION_PATH}?messageId=${FIRST_ANSWER_ID}`,
    design: {
      type: "figma",
      url: "https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=2106-28974",
    },
  },
};
