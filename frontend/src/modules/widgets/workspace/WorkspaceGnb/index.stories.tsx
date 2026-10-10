import DockablePanel from "@composites/DockablePanel";
import SearchEvidenceToggle from "@features/search/SearchEvidenceToggle";
import { getRouterPath, PATH_ROUTE } from "@routes/PATH_ROUTE";
import type { Meta, StoryObj } from "@storybook/react-webpack5";
import WorkspaceSidebar from "@widgets/workspace/WorkspaceSidebar";
import { MemoryRouter, Route, Routes } from "react-router";

import SidebarIcon from "@/assets/icons/sidebar.svg";

import WorkspaceGnb from ".";

const WORKSPACE_ID = "1";
const HOME_PATH = getRouterPath({
  routeKey: "WORKSPACE_HOME",
  params: { workspaceId: WORKSPACE_ID },
});
const CHAT_PATH = getRouterPath({
  routeKey: "CHAT",
  params: { workspaceId: WORKSPACE_ID },
});
const CONVERSATION_PATH = getRouterPath({
  routeKey: "CHAT_SESSION",
  params: { workspaceId: WORKSPACE_ID, sessionId: "10" },
});
const DOCUMENTS_PATH = getRouterPath({
  routeKey: "DOCUMENTS",
  params: { workspaceId: WORKSPACE_ID },
});
const DOCK_RAIL_ID = "story-dock-rail";

/**
 * 워크스페이스 안의 모든 화면 맨 위에 떠 있는 상단바(GNB)예요. 워크스페이스 홈 · 탐색 · 문서 화면이 함께 써요.
 *
 * **구성**
 * - 왼쪽: 사이드바 같은 패널을 여는 버튼들이에요. 어떤 패널을 둘지는 화면마다 달라(대화 목록은 탐색 화면에만 있어요) 화면 레이아웃이 넣어 줘요.
 *   이 스토리에는 모든 화면에 공통인 사이드바 버튼만 넣었어요.
 * - 가운데: 홈 · 탐색 · 문서를 오가는 내비 필이에요. 지금 있는 화면이 채워진 모양으로 표시돼요.
 * - 오른쪽: 오른쪽 패널을 여는 버튼과 로그인한 사람의 GitHub 프로필 이미지예요. 프로필은 지금은 보여 주기만 하고 누를 수 없어요.
 *   오른쪽 패널 버튼(찾은 기록)은 탐색 화면에만 있어 왼쪽 버튼처럼 화면 레이아웃이 넣어 줘요.
 *
 * **동작 규칙**
 * - 배경 없이 본문 위에 떠 있어요.
 * - 좌우 영역이 같은 비율로 늘어나 내비 필은 늘 화면 한가운데에 놓여요.
 * - 지금 있는 화면의 버튼은 눌러도 이동하지 않아 뒤로 가기 기록이 같은 화면으로 쌓이지 않아요. 탐색 화면은 대화를 연 상태여도 탐색으로 표시돼요.
 * - 문서 하나를 보는 화면도 문서로 표시되고, 거기서 「문서」를 누르면 문서 목록으로 가요. 문서 탭은 언제나 폴더별 목록을 보여 주기 때문이에요.
 * - 프로필은 정보를 받기 전에는 기본 모양으로, 이미지가 없거나 불러오지 못하면 닉네임 첫 글자로 대신해요.
 */
const meta = {
  title: "Workspace/WorkspaceGnb",
  component: WorkspaceGnb,
  parameters: {
    layout: "fullscreen",
    initialPath: HOME_PATH,
    design: [
      {
        name: "GNB/Floating nav=홈",
        type: "figma",
        url: "https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=1364-6863",
      },
      {
        name: "Pill",
        type: "figma",
        url: "https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=1364-839",
      },
    ],
  },
  args: {
    left: (
      <DockablePanel
        label="사이드바"
        icon={<SidebarIcon size={18} />}
        dockTargetId={DOCK_RAIL_ID}
      >
        <WorkspaceSidebar />
      </DockablePanel>
    ),
  },
  argTypes: { left: { control: false }, right: { control: false } },
  decorators: [
    (Story, { parameters }) => (
      <MemoryRouter initialEntries={[parameters.initialPath]}>
        <Routes>
          {/* 찾은 기록 버튼이 대화 ID를 읽도록 대화 경로를 따로 둬요 */}
          {[`${PATH_ROUTE.WORKSPACE_HOME}/*`, PATH_ROUTE.CHAT_SESSION].map(
            (path) => (
              <Route
                key={path}
                path={path}
                element={
                  // 화면 레이아웃처럼 GNB 위에 여백을, 아래에 사이드바를 고정할 자리를 둬요
                  <div style={{ paddingTop: "1.5rem" }}>
                    <Story />
                    <div
                      id={DOCK_RAIL_ID}
                      style={{
                        display: "flex",
                        height: "30rem",
                        padding: "1.25rem 0 0 2.5rem",
                      }}
                    />
                  </div>
                }
              />
            ),
          )}
        </Routes>
      </MemoryRouter>
    ),
  ],
} satisfies Meta<typeof WorkspaceGnb>;

export default meta;

type Story = StoryObj<typeof meta>;

/** 워크스페이스 홈에서 보이는 모양이에요. 내비 필의 「홈」이 채워져 있어요. */
export const OnHomeScreen: Story = {};

/** 탐색 화면에서 보이는 모양이에요. 내비 필의 「탐색」이 채워져 있어요. */
export const OnChatScreen: Story = {
  parameters: {
    initialPath: CHAT_PATH,
    design: {
      type: "figma",
      url: "https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=1364-7028",
    },
  },
};

/** 질문과 답변이 오간 탐색 대화에서 보이는 모양이에요. 근거가 있는 답변이 있어 아바타 앞에 찾은 기록 버튼이 있어요. */
export const OnConversationScreen: Story = {
  args: { right: <SearchEvidenceToggle /> },
  parameters: {
    initialPath: CONVERSATION_PATH,
    design: {
      type: "figma",
      url: "https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=2106-28969",
    },
  },
};

/**
 * 문서 목록 화면에서 보이는 모양이에요. 내비 필의 「문서」가 채워져 있어요.
 * 문서 하나를 보는 화면에서도 같은 모양이고, 거기서 「문서」를 누르면 문서 목록으로 가요.
 */
export const OnDocumentsScreen: Story = {
  parameters: {
    initialPath: DOCUMENTS_PATH,
    design: {
      type: "figma",
      url: "https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=2017-36056",
    },
  },
};
