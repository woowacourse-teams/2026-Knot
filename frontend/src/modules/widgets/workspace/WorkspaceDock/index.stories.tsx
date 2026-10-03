import { PATH_ROUTE, getRouterPath } from "@routes/PATH_ROUTE";
import type { Meta, StoryObj } from "@storybook/react-webpack5";
import { MemoryRouter, Route, Routes } from "react-router";
import { userEvent, within } from "storybook/test";

import WorkspaceDock from ".";
import {
  DOCK_HINT_MAX_SEEN_COUNT,
  DOCK_HINT_SEEN_COUNT_KEY,
  DOCK_HINT_VISIT_KEY,
} from "./constants/dockHint";

const WORKSPACE_ID = "1";
const HOME_PATH = getRouterPath({
  routeKey: "WORKSPACE_HOME",
  params: { workspaceId: WORKSPACE_ID },
});
const CHAT_PATH = getRouterPath({
  routeKey: "CHAT",
  params: { workspaceId: WORKSPACE_ID },
});

/** 안내를 이미 다 본 사용자로 만들어요. 안내 횟수는 브라우저에 남아 스토리끼리 이어지기 때문이에요. */
const markHintAsSeen = () => {
  localStorage.setItem(
    DOCK_HINT_SEEN_COUNT_KEY,
    String(DOCK_HINT_MAX_SEEN_COUNT),
  );
  sessionStorage.removeItem(DOCK_HINT_VISIT_KEY);
};

/**
 * 워크스페이스 화면 하단 가운데에 떠 있는 독이에요. 어느 화면에서든 AI에게 바로 질문을 던지는 입구예요.
 *
 * **동작 규칙**
 * - 접혀 있을 때는 동그란 버튼 하나예요. 누르면 질문 입력창으로 폭이 벌어지며 펼쳐지고, 커서가 바로 입력창에 놓여요.
 * - 입력창을 누르지 않고 화면 아무 데서나 글자를 쳐도 독이 펼쳐지며 그 글자가 그대로 이어져요.
 * - 이 방법은 처음 세 번의 방문까지만 독 위의 말풍선으로 알려 줘요. 같은 방문 안에서 새로고침하거나 홈과 탐색을 오가도 한 번으로 셉니다. 독을 열면 이번 방문에는 다시 띄우지 않아요.
 * - 펼친 뒤 독 바깥을 누르거나 `Esc`를 누르면 다시 접혀요. 바깥을 눌러 접을 때는 적던 글을 지우지 않고 다시 열 때 그대로 돌려줘요. `Esc`는 글까지 비워요.
 * - `Enter`로 보내고 `Shift+Enter`로 줄을 바꿔요. 입력이 비어 있으면 보내기 버튼을 누를 수 없어요. 입력창은 다섯 줄까지 자라고 그보다 길면 안에서 스크롤해요.
 * - 홈에서 질문을 보내면 탐색 화면으로 옮겨 가며 그 질문으로 대화를 시작해요.
 * - 탐색 화면은 채팅이 곧 화면이라 독을 늘 펼쳐 둬요. 그 화면에서 보낸 질문은 아직 받을 곳이 없어 입력만 비워요. 탐색 v2에서 대화와 다시 연결해요.
 * - Figma에 숨겨져 있는 회의 녹음·글 작성 자리는 만들지 않았어요.
 *
 * **디자인 원본**: [Dock/Bar](https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=1347-862) ·
 * [Dock/Bar 모드=채팅 입력](https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=1379-1834) ·
 * [Tooltip · 독 안내](https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=1422-28)
 */
const meta = {
  title: "Workspace/WorkspaceDock",
  component: WorkspaceDock,
  parameters: { layout: "padded", initialPath: HOME_PATH },
  beforeEach: markHintAsSeen,
  decorators: [
    (Story, { parameters }) => (
      <MemoryRouter initialEntries={[parameters.initialPath]}>
        <Routes>
          <Route
            path={`${PATH_ROUTE.WORKSPACE_HOME}/*`}
            element={
              // 독 위로 뜨는 안내 말풍선이 잘리지 않게 위쪽에 자리를 둬요
              <div
                style={{
                  display: "flex",
                  justifyContent: "center",
                  paddingTop: "5rem",
                }}
              >
                <Story />
              </div>
            }
          />
        </Routes>
      </MemoryRouter>
    ),
  ],
} satisfies Meta<typeof WorkspaceDock>;

export default meta;

type Story = StoryObj<typeof meta>;

/** 홈에서 처음 보이는 접힌 모양이에요. 안내를 이미 다 본 사용자예요. */
export const Collapsed: Story = {};

/** 처음 몇 번의 방문에만 보이는 안내 말풍선이에요. 예: 워크스페이스에 처음 들어온 사용자 */
export const FirstVisitHint: Story = {
  beforeEach: () => {
    localStorage.removeItem(DOCK_HINT_SEEN_COUNT_KEY);
    sessionStorage.removeItem(DOCK_HINT_VISIT_KEY);
  },
};

/** 독을 펼쳐 질문을 적은 상태예요. 보내기 버튼을 누를 수 있게 돼요. */
export const WithQuestion: Story = {
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);

    await userEvent.click(
      canvas.getByRole("button", { name: "무엇이든 요청하기" }),
    );
    await userEvent.type(
      canvas.getByRole("textbox", { name: "무엇이든 요청하세요" }),
      "지난주 회의에서 정해진 것만 뽑아 줘",
    );
  },
};

/** 탐색 화면에서는 독이 늘 펼쳐져 있어요. 아직 적은 글이 없어 보내기 버튼을 누를 수 없어요. */
export const OnChatScreen: Story = {
  parameters: { initialPath: CHAT_PATH },
};
