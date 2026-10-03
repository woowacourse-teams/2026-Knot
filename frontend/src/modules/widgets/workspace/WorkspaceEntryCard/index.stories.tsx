import { PATH_ROUTE } from "@routes/PATH_ROUTE";
import type { Meta, StoryObj } from "@storybook/react-webpack5";
import { MemoryRouter, Route, Routes } from "react-router";

import WorkspaceEntryCard from ".";

/**
 * 가입을 마친 사용자가 새 워크스페이스를 만들지, 초대 코드로 참여할지 고르는 카드예요. 워크스페이스 선택 화면(`/workspace`)에 놓여요.
 *
 * **동작 규칙**
 * - 「새 워크스페이스 만들기」는 워크스페이스 생성 화면(`/workspace/create`)으로, 「초대 코드로 참여하기」는 초대 코드 입력 화면(`/workspace/code`)으로 이어져요.
 * - 서버에 요청하지 않고 두 화면으로 나눠 보내기만 해요.
 * - 로고와 화면 가운데 배치는 화면 레이아웃이 맡아요. 이 카드는 카드 모양만 그려요.
 *
 * **디자인 원본**: [Card/Onboarding/Workspace](https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=432-1750)
 */
const meta = {
  title: "Workspace/WorkspaceEntryCard",
  component: WorkspaceEntryCard,
  parameters: { layout: "centered" },
  decorators: [
    (Story) => (
      <MemoryRouter initialEntries={[PATH_ROUTE.WORKSPACE]}>
        <Routes>
          <Route path={PATH_ROUTE.WORKSPACE} element={<Story />} />
          <Route path="*" element={<p>고른 화면으로 이동했어요.</p>} />
        </Routes>
      </MemoryRouter>
    ),
  ],
} satisfies Meta<typeof WorkspaceEntryCard>;

export default meta;

type Story = StoryObj<typeof meta>;

/** 워크스페이스가 아직 없는 사용자가 처음 보는 상태예요. */
export const Default: Story = {};
