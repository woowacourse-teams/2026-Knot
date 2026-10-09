import { INVITATION_PREVIEW_API_PATH } from "@api/fetch/api/v1/invitations/[tokenOrCode]";
import {
  invitationPreviewResponse,
  workspaceInvitationResponse,
} from "@api/mock/responses/workspaceInvitation";
import { getRouterPath, PATH_ROUTE } from "@routes/PATH_ROUTE";
import type { Meta, StoryObj } from "@storybook/react-webpack5";
import { delay, http, HttpResponse } from "msw";
import { MemoryRouter, Route, Routes } from "react-router";

import WorkspaceInviteLinkGate from ".";

const TOKEN = workspaceInvitationResponse.linkToken;
const INVITE_PATH = getRouterPath({
  routeKey: "INVITE",
  params: { token: TOKEN },
});

/**
 * 초대 링크(`/invite/:token`)로 들어온 사용자를 잠깐 붙잡아 두고 링크가 유효한지 확인하는 화면이에요.
 * 자기 화면은 없고, 확인하는 동안 스피너만 보여 줘요.
 *
 * **동작 규칙**
 * - 링크의 토큰으로 초대 미리보기를 조회해요. 토큰은 받은 그대로 보내고 대소문자·공백을 고치지 않아요.
 * - 유효하면 그 워크스페이스의 입장 확인 화면(`/workspace/:workspaceId/join`)으로 가요. 입장 확인 화면이 워크스페이스 이름과 토큰을 다시 묻지 않도록 함께 넘겨요.
 * - 없는 초대·만료·너무 잦은 시도·서버 오류·네트워크 끊김 등 어떤 이유로든 실패하면 초대 링크 오류 화면(`/join-error`)으로 가요.
 * - 어느 쪽으로 가든 방문 기록을 바꿔치기해서, 뒤로 가기를 눌러도 이 확인 화면으로 돌아오지 않아요.
 * - 화면에는 스피너만 보이지만 스크린리더에는 「초대 링크를 확인하고 있어요」라고 읽혀요.
 * - 로고와 화면 가운데 배치는 화면 레이아웃이 맡아요. 이 화면은 스피너 자리만 그려요.
 */
const meta = {
  title: "Workspace/WorkspaceInviteLinkGate",
  component: WorkspaceInviteLinkGate,
  parameters: {
    layout: "centered",
    design: [
      {
        name: "초대 링크로 워크스페이스 입장",
        type: "figma",
        url: "https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=600-10180",
      },
      {
        name: "올바르지 않은 초대 링크 접근",
        type: "figma",
        url: "https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=600-10148",
      },
    ],
  },
  decorators: [
    (Story) => (
      <MemoryRouter initialEntries={[INVITE_PATH]}>
        <Routes>
          <Route path={PATH_ROUTE.INVITE} element={<Story />} />
        </Routes>
      </MemoryRouter>
    ),
  ],
} satisfies Meta<typeof WorkspaceInviteLinkGate>;

export default meta;

type Story = StoryObj<typeof meta>;

/** 링크를 확인하는 동안 보이는 모습이에요. 실제로는 대개 눈 깜짝할 새에 지나가요. */
export const Checking: Story = {
  parameters: {
    // 문서 페이지에서는 스토리를 한 화면에 함께 그려 응답이 섞이므로, 이 스토리는 따로 그려요
    docs: { story: { inline: false, iframeHeight: 80 } },
    msw: {
      handlers: {
        invitationPreview: http.get(
          `*${INVITATION_PREVIEW_API_PATH(TOKEN)}`,
          async () => {
            await delay("infinite");
            return HttpResponse.json(invitationPreviewResponse);
          },
        ),
      },
    },
  },
};
