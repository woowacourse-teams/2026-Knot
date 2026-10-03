import {
  invitationPreviewResponse,
  workspaceInvitationResponse,
} from "@api/mock/responses/workspaceInvitation";
import { PATH_ROUTE } from "@routes/PATH_ROUTE";
import type { Meta, StoryObj } from "@storybook/react-webpack5";
import { delay, http, HttpResponse } from "msw";
import { MemoryRouter, Route, Routes } from "react-router";
import { userEvent, within } from "storybook/test";

import WorkspaceCodeCard from ".";

// 경로 상수가 코드를 받아 채우는 함수라 기존 핸들러처럼 패턴을 직접 적어요
const INVITATION_PREVIEW_URL = "*/api/v1/invitations/:tokenOrCode";

/** 팀에서 받은 6자리 참여 코드를 적어요. 다 채우면 바로 확인이 시작돼요. */
const typeCode = async ({ canvasElement }: { canvasElement: HTMLElement }) => {
  const canvas = within(canvasElement);

  await userEvent.type(
    canvas.getByRole("textbox", { name: "참여 코드" }),
    workspaceInvitationResponse.code,
  );
};

/** 코드 확인이 이 상태 코드로 실패하게 해요. */
const previewFails = (status: number) => ({
  msw: {
    handlers: {
      invitationPreview: http.get(
        INVITATION_PREVIEW_URL,
        () => new HttpResponse(null, { status }),
      ),
    },
  },
});

/**
 * 팀에서 전달받은 참여 코드를 입력해 워크스페이스에 들어가는 카드예요. 초대 코드 입력 화면(`/workspace/code`)에 놓여요.
 *
 * **동작 규칙**
 * - 6자리를 다 채우면 버튼 없이 바로 코드를 확인해요.
 * - 확인하는 동안에는 입력을 잠그고 오른쪽에 스피너를 보여 줘요.
 * - 맞는 코드면 오른쪽 체크와 「확인됐어요. 곧 다음 단계로 이동해요.」를 1.5초 동안 보여 준 뒤 그 워크스페이스의 입장 확인 화면(`/workspace/:workspaceId/join`)으로 넘어가요.
 *   입력한 코드와 워크스페이스 이름을 함께 넘겨 입장 확인 화면이 다시 묻지 않아요.
 * - 실패하면 빨간 테두리와 함께 이유를 입력창 아래에 띄우고 잠금을 풀어요. 값을 고치면 문구가 사라져요.
 *   없거나 만료된 코드(404), 너무 잦은 시도(429), 그 밖의 실패를 나눠 알려요. 그 밖의 실패는 코드가 틀렸다고 단정할 수 없어 확인하지 못했다고만 알려요.
 * - 입력은 서버 계약(ADR 243)과 같이 대문자로 보여 주고, 7자째부터는 입력되지 않아요.
 * - 로고와 화면 가운데 배치는 화면 레이아웃이 맡아요. 이 카드는 카드 모양만 그려요.
 *
 * **디자인 원본**: [초대 코드 입력](https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=600-10168) ·
 * [입력 에러](https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=600-10172) ·
 * [Field/TextField/Code](https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=443-910) ·
 * [인증 완료](https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=664-552)
 */
const meta = {
  title: "Workspace/WorkspaceCodeCard",
  component: WorkspaceCodeCard,
  parameters: { layout: "centered" },
  decorators: [
    (Story) => (
      <MemoryRouter initialEntries={[PATH_ROUTE.WORKSPACE_CODE]}>
        <Routes>
          <Route path={PATH_ROUTE.WORKSPACE_CODE} element={<Story />} />
          <Route
            path={PATH_ROUTE.WORKSPACE_JOIN}
            element={<p>입장 확인 화면으로 이동했어요.</p>}
          />
        </Routes>
      </MemoryRouter>
    ),
  ],
} satisfies Meta<typeof WorkspaceCodeCard>;

export default meta;

type Story = StoryObj<typeof meta>;

/** 코드 입력 화면에 처음 들어온 상태예요. */
export const Empty: Story = {};

/** 6자리를 채워 코드를 확인하는 중이에요. 입력이 잠겨요. */
export const Verifying: Story = {
  parameters: {
    msw: {
      handlers: {
        invitationPreview: http.get(INVITATION_PREVIEW_URL, async () => {
          await delay("infinite");
          return HttpResponse.json(invitationPreviewResponse);
        }),
      },
    },
  },
  play: typeCode,
};

/** 맞는 코드라 확인을 마친 상태예요. 1.5초 뒤 입장 확인 화면으로 넘어가요. */
export const Verified: Story = {
  play: typeCode,
};

/** 없거나 만료된 코드예요(404). 예: 오타, 재발급 전의 옛 코드 */
export const InvalidCode: Story = {
  parameters: previewFails(404),
  play: typeCode,
};

/** 짧은 시간에 코드를 너무 많이 시도한 상태예요(429). 잠시 기다리라고 안내해요. */
export const TooManyAttempts: Story = {
  parameters: previewFails(429),
  play: typeCode,
};

/** 서버 오류처럼 코드가 맞는지 알 수 없이 실패한 상태예요. 잠시 후 다시 시도하라고 안내해요. */
export const VerifyFailed: Story = {
  parameters: previewFails(500),
  play: typeCode,
};
