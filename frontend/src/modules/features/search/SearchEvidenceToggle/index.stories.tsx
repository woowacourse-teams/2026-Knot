import { PATH_ROUTE, getRouterPath } from "@routes/PATH_ROUTE";
import type { Meta, StoryObj } from "@storybook/react-webpack5";
import { MemoryRouter, Route, Routes } from "react-router";

import SearchEvidenceToggle from ".";

const CONVERSATION_PATH = getRouterPath({
  routeKey: "CHAT_SESSION",
  params: { workspaceId: "1", sessionId: "10" },
});
/** 예시 대화에서 근거가 2개인 첫 답변의 ID */
const FIRST_ANSWER_ID = 2;

/**
 * GNB 오른쪽에서 찾은 기록 패널을 여닫는 버튼이에요. 탐색 화면에서만 둬요.
 *
 * **동작 규칙**
 * - 근거가 있는 답변이 하나라도 있을 때만 보여요.
 * - 닫혀 있을 때 누르면 근거가 있는 가장 최근 답변의 찾은 기록을 열어요.
 * - 열려 있으면 채워진 모양이고, 누르면 닫아요.
 * - 왼쪽 패널 버튼이 왼쪽 패널을, 이 버튼이 오른쪽 패널을 여닫는 대칭 구조라 왼쪽 버튼과 같은 모양이에요.
 * - 이름(「찾은 기록 보기」·「찾은 기록 닫기」)은 화면에 쓰지 않고 스크린리더에만 알려요.
 */
const meta = {
  title: "Search/SearchEvidenceToggle",
  component: SearchEvidenceToggle,
  parameters: {
    initialPath: CONVERSATION_PATH,
    design: {
      type: "figma",
      url: "https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=2106-28969",
    },
  },
  decorators: [
    (Story, { parameters }) => (
      <MemoryRouter initialEntries={[parameters.initialPath]}>
        <Routes>
          <Route path={PATH_ROUTE.CHAT_SESSION} element={<Story />} />
        </Routes>
      </MemoryRouter>
    ),
  ],
} satisfies Meta<typeof SearchEvidenceToggle>;

export default meta;

type Story = StoryObj<typeof meta>;

/** 찾은 기록이 닫혀 있을 때예요. 누르면 가장 최근 답변의 찾은 기록이 열려 채워진 모양이 돼요. */
export const Default: Story = {};

/** 찾은 기록이 열려 있을 때예요. 누르면 닫혀요. */
export const Open: Story = {
  parameters: {
    initialPath: `${CONVERSATION_PATH}?messageId=${FIRST_ANSWER_ID}`,
    design: {
      type: "figma",
      url: "https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=2106-28974",
    },
  },
};
