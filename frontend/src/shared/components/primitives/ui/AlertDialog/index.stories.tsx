import type { Meta, StoryObj } from "@storybook/react-webpack5";
import { fn } from "storybook/test";

import SyncIcon from "@/assets/icons/sync.svg";

import AlertDialog from ".";

/**
 * 일어난 일을 알리는 알림 모달 카드예요. 사용자가 고를 것 없이 「확인」만 누르면 되는 안내에 씁니다.
 *
 * - 제목·안내와 가로를 채운 확인 버튼 하나를 그려요. 고를 것이 있으면 `ConfirmDialog`를 써요.
 * - `icon`을 넘기면 제목 앞에 붙어요. 아이콘 색은 넘기는 쪽에서 정해요.
 * - 안내는 한 줄이 기본이고 최대 두 줄이에요. 줄바꿈은 의미 단위로 직접 넣어요.
 *
 * **동작 규칙**
 * - 카드 모양만 그려요. 화면 가운데 띄우기·ESC·포커스 가두기는 없어서, 쓰는 쪽이 `Dim` 위에 올리고 `role`·`aria-*`도 함께 넘겨요.
 *
 * **디자인 원본**: [Dialog 유형=알림](https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=1814-5058)
 */
const meta = {
  title: "Shared/AlertDialog",
  component: AlertDialog,
  args: {
    title: "녹음을 저장하고 있어요",
    description: "저장이 끝나면 저절로 닫혀요.",
    confirmLabel: "확인",
    onConfirm: fn(),
  },
} satisfies Meta<typeof AlertDialog>;

export default meta;

type Story = StoryObj<typeof meta>;

/** 제목과 안내만 있는 기본 모양이에요. 예: 녹음 저장 중 안내 */
export const Default: Story = {};

/** 제목 앞에 아이콘을 붙여 무슨 일인지 한눈에 보이게 해요. */
export const WithIcon: Story = {
  args: { icon: <SyncIcon size={24} /> },
};
