import { INVITATIONS_ACCEPT_API_PATH } from "@api/fetch/api/v1/invitations/accept";
import {
  invitationAcceptanceResponse,
  invitationPreviewResponse,
  workspaceInvitationResponse,
} from "@api/mock/responses/workspaceInvitation";
import { getRouterPath, PATH_ROUTE } from "@routes/PATH_ROUTE";
import type { Meta, StoryObj } from "@storybook/react-webpack5";
import { delay, http, HttpResponse } from "msw";
import { MemoryRouter, Route, Routes } from "react-router";
import { userEvent, within } from "storybook/test";

import type { WorkspaceJoinState } from "@/shared/types/workspaceJoin";

import WorkspaceJoinCard from ".";

const JOIN_PATH = getRouterPath({
  routeKey: "WORKSPACE_JOIN",
  params: { workspaceId: String(invitationPreviewResponse.workspaceId) },
});

/** 초대 코드 입력 화면이나 초대 링크 확인 화면이 넘겨주는 값이에요 */
const JOIN_STATE: WorkspaceJoinState = {
  credential: workspaceInvitationResponse.code,
  workspaceName: invitationPreviewResponse.workspaceName,
};

/**
 * 초대 코드를 입력했거나 초대 링크를 타고 온 사용자에게 어느 워크스페이스에 합류하는지 보여 주는 입장 확인 카드예요.
 * 입장 확인 화면(`/workspace/:workspaceId/join`)에 놓여요.
 *
 * **동작 규칙**
 * - 「참여할게요」를 누르면 워크스페이스에 참여하고 그 워크스페이스 홈으로 가요. 이미 멤버여도 똑같이 홈으로 가요.
 * - 참여 요청 중에는 버튼이 로딩으로 바뀌어 두 번 눌리지 않아요.
 * - 워크스페이스 이름과 참여에 쓸 코드·토큰은 앞 화면이 넘겨준 값이에요. 새로고침·주소 직접 입력처럼 넘겨받은 값이 없으면 참여할 근거가 없으니 아무것도 그리지 않고 워크스페이스 선택 화면(`/workspace`)으로 돌려보내요.
 * - 로그인이 풀렸으면 로그인 화면으로, 초대가 없거나 만료됐거나 너무 자주 시도했으면 초대 링크 오류 화면(`/join-error`)으로 가요.
 * - 그 밖의 실패(서버 오류·네트워크 끊김)는 화면을 옮기지 않고 버튼을 다시 열어 다시 누를 수 있게 해요.
 * - 위 이동은 모두 방문 기록을 바꿔치기해서, 뒤로 가기를 눌러도 이 카드로 돌아오지 않아요.
 * - 로고와 화면 가운데 배치는 화면 레이아웃이 맡아요. 이 카드는 카드 모양만 그려요.
 *
 * **디자인 원본**: [초대 링크로 워크스페이스 입장](https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=600-10180) ·
 * [초대 코드 입력/워크스페이스 입장](https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=600-10176) ·
 * [Card/Onboarding & Workspace](https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=443-801)
 */
const meta = {
  title: "Workspace/WorkspaceJoinCard",
  component: WorkspaceJoinCard,
  parameters: { layout: "centered", joinState: JOIN_STATE },
  decorators: [
    (Story, { parameters }) => (
      <MemoryRouter
        initialEntries={[{ pathname: JOIN_PATH, state: parameters.joinState }]}
      >
        <Routes>
          <Route path={PATH_ROUTE.WORKSPACE_JOIN} element={<Story />} />
          <Route
            path={PATH_ROUTE.WORKSPACE_HOME}
            element={<p>워크스페이스 홈으로 이동했어요.</p>}
          />
          <Route
            path={PATH_ROUTE.WORKSPACE}
            element={<p>워크스페이스 선택 화면으로 이동했어요.</p>}
          />
          <Route
            path={PATH_ROUTE.JOIN_ERROR}
            element={<p>초대 링크 오류 화면으로 이동했어요.</p>}
          />
          <Route
            path={PATH_ROUTE.LOGIN}
            element={<p>로그인 화면으로 이동했어요.</p>}
          />
        </Routes>
      </MemoryRouter>
    ),
  ],
} satisfies Meta<typeof WorkspaceJoinCard>;

export default meta;

type Story = StoryObj<typeof meta>;

const clickJoin: Story["play"] = async ({ canvasElement }) => {
  const canvas = within(canvasElement);

  await userEvent.click(canvas.getByRole("button", { name: "참여할게요" }));
};

/** 초대를 확인하고 들어와 참여를 기다리는 상태예요. 「참여할게요」를 누르면 워크스페이스 홈으로 가요. */
export const Default: Story = {};

/** 「참여할게요」를 누른 뒤 응답을 기다리는 중이에요. 버튼이 로딩으로 잠겨요. */
export const Joining: Story = {
  parameters: {
    msw: {
      handlers: {
        acceptInvitation: http.post(
          `*${INVITATIONS_ACCEPT_API_PATH}`,
          async () => {
            await delay("infinite");
            return HttpResponse.json(invitationAcceptanceResponse, {
              status: 201,
            });
          },
        ),
      },
    },
  },
  play: clickJoin,
};

/** 카드를 띄워 둔 사이 초대가 만료되었거나 취소됐을 때예요. 너무 자주 시도했을 때도 같은 곳으로 가요. */
export const InvitationRejected: Story = {
  parameters: {
    msw: {
      handlers: {
        acceptInvitation: http.post(
          `*${INVITATIONS_ACCEPT_API_PATH}`,
          () => new HttpResponse(null, { status: 404 }),
        ),
      },
    },
  },
  play: clickJoin,
};

/** 카드를 띄워 둔 사이 로그인이 풀렸을 때예요. */
export const LoggedOut: Story = {
  parameters: {
    msw: {
      handlers: {
        acceptInvitation: http.post(
          `*${INVITATIONS_ACCEPT_API_PATH}`,
          () => new HttpResponse(null, { status: 401 }),
        ),
      },
    },
  },
  play: clickJoin,
};

/** 서버 오류처럼 일시적으로 참여하지 못했을 때예요. 화면은 그대로 두고 버튼을 다시 열어 다시 누를 수 있어요. */
export const JoinFailed: Story = {
  parameters: {
    msw: {
      handlers: {
        acceptInvitation: http.post(
          `*${INVITATIONS_ACCEPT_API_PATH}`,
          () => new HttpResponse(null, { status: 500 }),
        ),
      },
    },
  },
  play: clickJoin,
};

/** 새로고침하거나 주소를 직접 입력해 들어왔을 때예요. 카드를 그리지 않고 워크스페이스 선택 화면으로 돌려보내요. */
export const WithoutJoinState: Story = {
  // undefined는 meta의 값을 덮지 못해 null로 비워요
  parameters: { joinState: null },
};
