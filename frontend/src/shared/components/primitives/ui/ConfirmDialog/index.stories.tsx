import type { Meta, StoryObj } from "@storybook/react-webpack5";
import { fn } from "storybook/test";

import ConfirmDialog from ".";

/**
 * 동작 전에 한 번 더 묻는 확인 모달 카드예요. 사용자가 진행할지 멈출지 골라야 할 때 씁니다.
 *
 * - 제목·안내와 보조(왼쪽)·주(오른쪽) 버튼 두 개를 같은 폭으로 그려요. 고를 것 없이 알리기만 하면 `AlertDialog`를 써요.
 * - 되돌릴 수 없는 동작이면 `isDestructive`로 주 버튼을 경고색으로 그려요.
 * - 안내는 한 줄이 기본이고 최대 두 줄이에요. 줄바꿈은 의미 단위로 직접 넣어요.
 *
 * **동작 규칙**
 * - 카드 모양만 그려요. 화면 가운데 띄우기·ESC·포커스 가두기는 없어서, 쓰는 쪽이 `Dim` 위에 올리고 `role`·`aria-*`도 함께 넘겨요.
 */
const meta = {
  title: "Shared/ConfirmDialog",
  component: ConfirmDialog,
  parameters: {
    design: {
      type: "figma",
      url: "https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=2024-11427",
    },
  },
  args: {
    title: "녹음을 끝낼까요?",
    description: "끝낸 녹음은 다시 이어 갈 수 없어요.",
    cancelLabel: "계속 녹음",
    confirmLabel: "녹음 끝내기",
    onCancel: fn(),
    onConfirm: fn(),
    isDestructive: false,
  },
} satisfies Meta<typeof ConfirmDialog>;

export default meta;

type Story = StoryObj<typeof meta>;

/** 기본 모양이에요. 주 버튼이 기본 색으로 그려져요. */
export const Default: Story = {};

/** 되돌릴 수 없는 동작을 물을 때예요. 주 버튼이 경고색으로 그려져요. */
export const Destructive: Story = {
  args: { isDestructive: true },
};
