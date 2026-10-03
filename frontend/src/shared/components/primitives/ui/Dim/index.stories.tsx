import type { Meta, StoryObj } from "@storybook/react-webpack5";
import { fn } from "storybook/test";

import ConfirmDialog from "@primitives/ui/ConfirmDialog";

import Dim from ".";

/**
 * 모달 뒤를 덮는 배경 막(스크림)이에요. `AlertDialog`·`ConfirmDialog` 같은 모달 카드를 띄울 때 그 뒤에 깝니다.
 *
 * - 화면 전체를 어둡게(Neutral/900 40%) 덮고, 넘긴 내용을 그 위 가운데에 둬요.
 * - 겹쳐 뜨는 패널보다 위에 그려져요.
 * - 바깥을 눌렀을 때 할 일 같은 동작은 없어요. 필요하면 쓰는 쪽이 `onClick`으로 붙여요.
 *
 * 실제 화면에서는 화면 전체를 덮어요. 스토리북에서는 문서 페이지를 가리지 않도록 정해진 틀 안에만 그려요.
 *
 * **디자인 원본**: [Dim](https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=2024-11436)
 */
const meta = {
  title: "Shared/Dim",
  component: Dim,
  decorators: [
    // transform이 있는 틀 안에서는 position: fixed가 화면이 아니라 틀을 기준으로 잡혀요
    (Story) => (
      <div style={{ height: "25rem", transform: "translateZ(0)" }}>
        <Story />
      </div>
    ),
  ],
} satisfies Meta<typeof Dim>;

export default meta;

type Story = StoryObj<typeof meta>;

/** 모달 카드를 띄운 모습이에요. 예: 녹음을 끝낼지 묻는 확인 모달 */
export const WithDialog: Story = {
  args: {
    onClick: fn(),
    children: (
      <ConfirmDialog
        role="dialog"
        aria-modal="true"
        aria-label="녹음을 끝낼까요?"
        title="녹음을 끝낼까요?"
        description="끝낸 녹음은 다시 이어 갈 수 없어요."
        cancelLabel="계속 녹음"
        confirmLabel="녹음 끝내기"
        onCancel={fn()}
        onConfirm={fn()}
      />
    ),
  },
};
