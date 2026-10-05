import { PATH_ROUTE, getRouterPath } from "@routes/PATH_ROUTE";
import type { Meta, StoryObj } from "@storybook/react-webpack5";
import { MemoryRouter, Route, Routes } from "react-router";

import SearchEvidenceList from ".";

const CONVERSATION_PATH = getRouterPath({
  routeKey: "CHAT_SESSION",
  params: { workspaceId: "1", sessionId: "10" },
});
/** 예시 대화에서 근거가 3개인 둘째 답변의 ID */
const ANSWER_WITH_THREE_EVIDENCES_ID = 4;

/**
 * 찾은 기록 패널이에요. 탐색 화면 오른쪽 레일에서, 지금 펼친 답변의 근거가 된 문서를 보여줘요.
 *
 * **동작 규칙**
 * - 문서는 관련도가 높은 순서로 위에서부터 최대 3개까지 놓여요.
 * - 답변 아래 「기록 N개에서 찾았어요」나 GNB 오른쪽 찾은 기록 버튼으로 열고 닫아요. 그래서 패널 안에는 닫기 버튼이 없어요.
 * - 펼친 답변이 없거나 그 답변에 근거가 없으면 패널을 띄우지 않아요.
 * - 카드를 누르면 그 문서 보기 화면으로 가요. 링크라 새 탭으로도 열 수 있어요.
 * - 아직 메시지 조회 API를 연결하지 않아 예시 대화의 답변을 그려요.
 */
const meta = {
  title: "Search/SearchEvidenceList",
  component: SearchEvidenceList,
  parameters: {
    layout: "padded",
    design: {
      type: "figma",
      url: "https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=2098-29280",
    },
  },
  decorators: [
    (Story) => (
      <MemoryRouter
        initialEntries={[
          `${CONVERSATION_PATH}?messageId=${ANSWER_WITH_THREE_EVIDENCES_ID}`,
        ]}
      >
        <Routes>
          <Route
            path={PATH_ROUTE.CHAT_SESSION}
            element={
              // 오른쪽 레일의 폭(400px)이에요
              <div style={{ width: "25rem" }}>
                <Story />
              </div>
            }
          />
        </Routes>
      </MemoryRouter>
    ),
  ],
} satisfies Meta<typeof SearchEvidenceList>;

export default meta;

type Story = StoryObj<typeof meta>;

/** 근거가 3개인 답변의 찾은 기록을 펼친 상태예요. 관련도 순으로 놓여요. */
export const Default: Story = {};
