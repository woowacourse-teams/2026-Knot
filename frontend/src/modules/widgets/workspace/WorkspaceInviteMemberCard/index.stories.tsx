import { WORKSPACE_INVITATIONS_API_PATH } from "@api/fetch/api/v1/workspaces/[workspaceId]/invitations";
import { workspaceInvitationResponse } from "@api/mock/responses/workspaceInvitation";
import { getRouterPath, PATH_ROUTE } from "@routes/PATH_ROUTE";
import type { Meta, StoryObj } from "@storybook/react-webpack5";
import { delay, http, HttpResponse } from "msw";
import { MemoryRouter, Route, Routes } from "react-router";

import WorkspaceInviteMemberCard from ".";

const WORKSPACE_ID = 1;
const HOME_PATH = getRouterPath({
  routeKey: "WORKSPACE_HOME",
  params: { workspaceId: String(WORKSPACE_ID) },
});

/**
 * 워크스페이스 홈에서 팀원을 부르는 카드예요. 초대 링크와 6자 초대 코드를 복사해 팀원에게 전해요.
 *
 * **동작 규칙**
 * - 링크와 코드는 지금 보고 있는 워크스페이스의 활성 초대에서 와요. 팀원 초대 화면의 카드와 같은 링크·코드예요.
 * - 「복사」를 누르면 전체 초대 링크를 복사하고, 버튼이 2초 동안 강조색 「복사됨」으로 바뀌어요.
 * - 「초대 코드 복사」를 누르면 6자 코드를 복사하고, 글자가 2초 동안 「복사됨」으로 바뀌어요.
 * - 복사 결과를 알림창으로 띄우지 않아요. 클립보드에 쓰지 못하면 화면 변화 없이 넘어가요.
 * - 초대를 받아 오기 전에는 두 버튼을 모두 막아 빈 값이 복사되지 않게 해요.
 * - 초대를 받아 오지 못해도 카드는 그대로 두고 버튼만 막아요. 다른 화면으로 보낼지는 워크스페이스 화면 전체가 정해요.
 */
const meta = {
  title: "Workspace/WorkspaceInviteMemberCard",
  component: WorkspaceInviteMemberCard,
  parameters: {
    layout: "centered",
    design: [
      {
        name: "Card/InviteMember",
        type: "figma",
        url: "https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=600-10087",
      },
      {
        name: "홈 화면/초대 링크 복사",
        type: "figma",
        url: "https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=600-10089",
      },
      {
        name: "Field/Copy",
        type: "figma",
        url: "https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=484-4925",
      },
    ],
  },
  decorators: [
    (Story) => (
      <MemoryRouter initialEntries={[HOME_PATH]}>
        <Routes>
          <Route path={PATH_ROUTE.WORKSPACE_HOME} element={<Story />} />
        </Routes>
      </MemoryRouter>
    ),
  ],
} satisfies Meta<typeof WorkspaceInviteMemberCard>;

export default meta;

type Story = StoryObj<typeof meta>;

/** 초대를 받아 와 바로 복사할 수 있는 상태예요. 버튼을 눌러 「복사됨」으로 바뀌는 모습을 볼 수 있어요. */
export const Default: Story = {};

/** 초대를 받아 오는 중이에요. 링크 칸은 비어 있고 두 버튼 모두 누를 수 없어요. */
export const Loading: Story = {
  parameters: {
    // 문서 페이지에서는 스토리를 한 화면에 함께 그려 응답이 섞이므로, 이 스토리는 따로 그려요
    docs: { story: { inline: false, iframeHeight: 240 } },
    msw: {
      handlers: {
        workspaceInvitations: http.post(
          `*${WORKSPACE_INVITATIONS_API_PATH(WORKSPACE_ID)}`,
          async () => {
            await delay("infinite");
            return HttpResponse.json(workspaceInvitationResponse);
          },
        ),
      },
    },
  },
};
