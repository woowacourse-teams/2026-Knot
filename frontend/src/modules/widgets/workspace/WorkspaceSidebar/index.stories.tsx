import { WORKSPACE_API_PATH } from "@api/fetch/api/v1/workspaces/[workspaceId]";
import { WORKSPACE_NOTION_PAGE_TREE_API_PATH } from "@api/fetch/api/v1/workspaces/[workspaceId]/notionPages/tree";
import { notionPageTreeResponse } from "@api/mock/responses/notionPage";
import { workspaceDetailResponse } from "@api/mock/responses/workspace";
import { getRouterPath, PATH_ROUTE } from "@routes/PATH_ROUTE";
import type { Meta, StoryObj } from "@storybook/react-webpack5";
import { delay, http, HttpResponse } from "msw";
import { MemoryRouter, Route, Routes } from "react-router";
import { userEvent, within } from "storybook/test";

import WorkspaceSidebar from ".";

const WORKSPACE_ID = 1;
const HOME_PATH = getRouterPath({
  routeKey: "WORKSPACE_HOME",
  params: { workspaceId: String(WORKSPACE_ID) },
});

const [product, roadmap] = notionPageTreeResponse;

/** 이름이 `name`으로 시작하는 폴더 행이에요. 행 이름 뒤에 문서 수가 붙어요 */
const folderRowName = (name: string) => new RegExp(`^${name}`);

/**
 * 워크스페이스 화면 왼쪽의 사이드바 드로어예요. 워크스페이스 이름과 Notion에서 가져온 페이지를 폴더 트리로 보여 줘요.
 *
 * **동작 규칙**
 * - 상단 메뉴 왼쪽의 사이드바 버튼으로 여닫아요. 버튼에 마우스를 올리면 본문 위에 겹쳐 뜨고, 누르면 왼쪽에 자리를 잡아요. 뜨는 방식과 자리는 감싸는 패널이 정하고, 이 드로어는 껍데기와 내용만 그려요.
 * - 위쪽에는 지금 보고 있는 워크스페이스의 이름이 나와요. 이름을 받아 오기 전에는 이름 자리를 비워 둬요.
 * - 「폴더」 아래에는 마지막으로 Notion에서 가져온 페이지들이 트리로 나와요.
 * - 하위 페이지가 있는 페이지는 폴더 행이에요. 오른쪽에 딸린 문서 수가 붙고, 누르면 펼치고 다시 누르면 접어요.
 * - 펼친 폴더 아래에는 하위 페이지가 한 단계 더 들여써져 나오고, 그 왼쪽에 부모 폴더 화살표의 가운데를 지나는 세로 안내선이 그어져요.
 * - 하위 페이지가 없는 페이지는 문서 행이에요. 아직 문서를 여는 기능이 없어 이름만 보여요.
 * - 처음에는 모든 폴더가 접혀 있어요. 사이드바를 닫았다 다시 열어도 펼쳐 둔 폴더는 그대로예요.
 * - 가져온 페이지가 없으면 「폴더」 라벨만 남아요.
 */
const meta = {
  title: "Workspace/WorkspaceSidebar",
  component: WorkspaceSidebar,
  parameters: {
    layout: "padded",
    design: [
      {
        name: "Sidebar/Drawer",
        type: "figma",
        url: "https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=1382-2171",
      },
      {
        name: "Sidebar/Workspace",
        type: "figma",
        url: "https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=580-1442",
      },
      {
        name: "Sidebar/FolderHead",
        type: "figma",
        url: "https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=1381-5285",
      },
      {
        name: "Sidebar/FolderRow",
        type: "figma",
        url: "https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=580-1444",
      },
      {
        name: "Sidebar/FileRow",
        type: "figma",
        url: "https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=588-523",
      },
    ],
  },
  decorators: [
    (Story) => (
      <MemoryRouter initialEntries={[HOME_PATH]}>
        <Routes>
          <Route
            path={PATH_ROUTE.WORKSPACE_HOME}
            element={
              // 드로어는 감싸는 패널의 높이를 그대로 채우므로 화면 높이만큼 자리를 줘요
              <div style={{ height: "40rem" }}>
                <Story />
              </div>
            }
          />
        </Routes>
      </MemoryRouter>
    ),
  ],
} satisfies Meta<typeof WorkspaceSidebar>;

export default meta;

type Story = StoryObj<typeof meta>;

/** 사이드바를 처음 열었을 때예요. 모든 폴더가 접혀 있어요. */
export const Default: Story = {};

/** 폴더를 펼쳐 하위 페이지를 본 상태예요. 예: 「제품」 아래 「로드맵」까지 펼침 */
export const FolderExpanded: Story = {
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);

    await userEvent.click(
      await canvas.findByRole("button", { name: folderRowName(product.title) }),
    );
    await userEvent.click(
      canvas.getByRole("button", { name: folderRowName(roadmap.title) }),
    );
  },
};

/** Notion에서 가져온 페이지가 아직 없을 때예요. 예: 가져오기를 하기 전인 새 워크스페이스 */
export const NoPages: Story = {
  parameters: {
    msw: {
      handlers: {
        notionPageTree: http.get(
          `*${WORKSPACE_NOTION_PAGE_TREE_API_PATH(WORKSPACE_ID)}`,
          () => HttpResponse.json([]),
        ),
      },
    },
  },
};

/** 워크스페이스 이름과 페이지를 받아 오는 중이에요. 이름 자리와 폴더 목록이 비어 있어요. */
export const Loading: Story = {
  parameters: {
    msw: {
      handlers: {
        workspace: http.get(
          `*${WORKSPACE_API_PATH(WORKSPACE_ID)}`,
          async () => {
            await delay("infinite");
            return HttpResponse.json(workspaceDetailResponse);
          },
        ),
        notionPageTree: http.get(
          `*${WORKSPACE_NOTION_PAGE_TREE_API_PATH(WORKSPACE_ID)}`,
          async () => {
            await delay("infinite");
            return HttpResponse.json(notionPageTreeResponse);
          },
        ),
      },
    },
  },
};
