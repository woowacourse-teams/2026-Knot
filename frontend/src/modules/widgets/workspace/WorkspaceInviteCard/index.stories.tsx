import { workspaceInvitationResponse } from "@api/mock/responses/workspaceInvitation";
import { getRouterPath, PATH_ROUTE } from "@routes/PATH_ROUTE";
import type { Meta, StoryObj } from "@storybook/react-webpack5";
import { delay, http, HttpResponse } from "msw";
import { MemoryRouter, Route, Routes } from "react-router";
import { spyOn, userEvent, within } from "storybook/test";

import WorkspaceInviteCard from ".";

const INVITE_PATH = getRouterPath({
  routeKey: "WORKSPACE_INVITE",
  params: { workspaceId: "1" },
});

// 경로 상수가 워크스페이스 ID를 받아 채우는 함수라 기존 핸들러처럼 패턴을 직접 적어요
const WORKSPACE_INVITATIONS_URL =
  "*/api/v1/workspaces/:workspaceId/invitations";

/**
 * 복사 결과만 보이도록 알림창과 클립보드를 대신해요.
 * 알림창이 뜨면 화면이 멈추고, 스토리북 화면은 클립보드 쓰기 권한이 없을 수 있어서예요.
 */
const fakeCopy = () => {
  const alertSpy = spyOn(window, "alert").mockImplementation(() => {});
  const clipboardSpy = spyOn(
    navigator.clipboard,
    "writeText",
  ).mockResolvedValue();

  return () => {
    alertSpy.mockRestore();
    clipboardSpy.mockRestore();
  };
};

/** 초대 조회가 이 상태 코드로 실패하게 해요. */
const invitationFails = (status: number) => ({
  msw: {
    handlers: {
      workspaceInvitations: http.post(
        WORKSPACE_INVITATIONS_URL,
        () => new HttpResponse(null, { status }),
      ),
    },
  },
});

/**
 * 워크스페이스를 만든 직후 팀원을 부르는 카드예요. 팀원 초대 화면(`/workspace/:workspaceId/invite`)에 놓여요.
 *
 * **동작 규칙**
 * - 참여 코드와 초대 링크를 보여 주고, 각각 눌러 클립보드로 복사해요.
 * - 복사하면 알림창으로 알리고, 코드 상자는 아이콘이 체크로, 링크 버튼은 「복사됨」으로 2초 동안 바뀌어요.
 *   클립보드에 쓰지 못하면 화면 변화 없이 조용히 넘어가요.
 * - 코드와 링크는 이 워크스페이스의 활성 초대에서 와요. 활성 초대가 없으면 이때 새로 발급돼요. 받기 전에는 복사할 수 없어요.
 * - 초대를 받지 못하면 로그인이 풀린 경우(401)는 로그인 화면으로, 멤버가 아니거나 없는 워크스페이스(403·404)는 워크스페이스 선택 화면으로 보내요.
 *   그 밖의 실패는 그 자리에 머물고 코드 상자가 빈 채로 남아요.
 * - 「다음」은 이 워크스페이스의 노션 연동 화면(`/workspace/:workspaceId/notion-connection`)으로 이어져요. 초대 조회 결과와 상관없이 누를 수 있어요.
 * - 로고와 화면 가운데 배치는 화면 레이아웃이 맡아요. 이 카드는 카드 모양만 그려요.
 */
const meta = {
  title: "Workspace/WorkspaceInviteCard",
  component: WorkspaceInviteCard,
  parameters: {
    layout: "centered",
    design: [
      {
        name: "새 워크스페이스 생성/참여 코드 및 링크 공유",
        type: "figma",
        url: "https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=432-1868",
      },
      {
        name: "새 워크스페이스 생성/참여 코드 복사",
        type: "figma",
        url: "https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=679-3120",
      },
      {
        name: "Card/CodeBox",
        type: "figma",
        url: "https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=691-1746",
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
      <MemoryRouter initialEntries={[INVITE_PATH]}>
        <Routes>
          <Route path={PATH_ROUTE.WORKSPACE_INVITE} element={<Story />} />
          <Route
            path={PATH_ROUTE.WORKSPACE_NOTION_CONNECTION}
            element={<p>노션 연동 화면으로 이동했어요.</p>}
          />
          <Route
            path={PATH_ROUTE.WORKSPACE}
            element={<p>워크스페이스 선택 화면으로 이동했어요.</p>}
          />
          <Route
            path={PATH_ROUTE.LOGIN}
            element={<p>로그인 화면으로 이동했어요.</p>}
          />
        </Routes>
      </MemoryRouter>
    ),
  ],
} satisfies Meta<typeof WorkspaceInviteCard>;

export default meta;

type Story = StoryObj<typeof meta>;

/** 초대를 받아 코드와 링크를 복사할 수 있는 상태예요. */
export const Default: Story = {};

/** 초대를 받아 오는 중이에요. 코드 상자와 복사 버튼에 스피너가 돌고 복사할 수 없어요. */
export const Loading: Story = {
  parameters: {
    msw: {
      handlers: {
        workspaceInvitations: http.post(WORKSPACE_INVITATIONS_URL, async () => {
          await delay("infinite");
          return HttpResponse.json(workspaceInvitationResponse);
        }),
      },
    },
  },
};

/** 코드 상자를 눌러 참여 코드를 복사한 직후예요. 아이콘이 2초 동안 체크로 바뀌어요. */
export const CodeCopied: Story = {
  parameters: {
    design: {
      type: "figma",
      url: "https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=704-3182",
    },
  },
  beforeEach: fakeCopy,
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);

    await userEvent.click(
      await canvas.findByRole("button", {
        name: `참여 코드 ${workspaceInvitationResponse.code} 복사`,
      }),
    );
  },
};

/** 링크의 「복사」를 눌러 초대 링크를 복사한 직후예요. 버튼이 2초 동안 「복사됨」으로 바뀌어요. */
export const LinkCopied: Story = {
  beforeEach: fakeCopy,
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);

    // 초대를 받기 전에는 복사 버튼이 잠겨 있어 코드가 보일 때까지 기다려요
    await canvas.findByText(workspaceInvitationResponse.code);
    await userEvent.click(canvas.getByRole("button", { name: "복사" }));
  },
};

/** 서버 오류로 초대를 받지 못한 상태예요. 화면에 머물고 코드 상자가 빈 채로 잠겨요. */
export const LoadFailed: Story = {
  parameters: invitationFails(500),
};

/** 멤버가 아닌 워크스페이스의 초대 화면에 들어온 상태(403)예요. 워크스페이스 선택 화면으로 보내요. */
export const AccessDenied: Story = {
  parameters: invitationFails(403),
};

/** 로그인이 풀린 채 들어온 상태(401)예요. 로그인 화면으로 보내요. */
export const SessionExpired: Story = {
  parameters: invitationFails(401),
};
