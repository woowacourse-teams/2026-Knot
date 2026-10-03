import type { Meta, StoryObj } from "@storybook/react-webpack5";
import { MemoryRouter } from "react-router";

import WorkspaceCreateButton from ".";

/**
 * 새 워크스페이스 생성 화면(`/workspace/create`)으로 이동하는 버튼이에요.
 *
 * 가입을 마친 사용자가 워크스페이스를 만들지, 초대 코드로 참여할지 고르는 카드에서 **주요 동작**으로 위에 둡니다.
 * 바로 아래에는 「또는」 구분선을 사이에 두고 보조 동작인 `Workspace/WorkspaceJoinByCodeButton`이 와요.
 *
 * - 누르면 화면만 옮기고 API는 부르지 않아요. 스토리북에서는 눌러도 화면이 바뀌지 않습니다.
 *
 * **쓰이는 화면**: [Card/Onboarding/Workspace](https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=432-1750)
 */
const meta = {
  title: "Workspace/WorkspaceCreateButton",
  component: WorkspaceCreateButton,
  decorators: [
    // 워크스페이스 생성 및 참여 카드의 내용 너비(460px - 좌우 여백 48px)에 맞춰 가로를 꽉 채워요
    (Story) => (
      <MemoryRouter>
        <div style={{ width: "22.75rem" }}>
          <Story />
        </div>
      </MemoryRouter>
    ),
  ],
} satisfies Meta<typeof WorkspaceCreateButton>;

export default meta;

type Story = StoryObj<typeof meta>;

/** 워크스페이스 생성 및 참여 카드에서 보이는 모습이에요. */
export const Default: Story = {};
