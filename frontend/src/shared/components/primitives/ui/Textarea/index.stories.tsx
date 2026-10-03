import type { Meta, StoryObj } from "@storybook/react-webpack5";

import Textarea from ".";

/**
 * 여러 줄 텍스트 입력창이에요. 예: 워크스페이스 하단 독의 「무엇이든 요청하세요」 입력창
 *
 * - 브라우저 기본 모양(테두리·여백·포커스 테두리·크기 조절 손잡이)만 지운 바탕이에요.
 * - 배경, 테두리, 글자, 높이 제한은 쓰는 쪽에서 `styled(Textarea)`로 정해요.
 *
 * **디자인 원본**: [Textarea](https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=506-7322)
 */
const meta = {
  title: "Shared/Textarea",
  component: Textarea,
  args: {
    placeholder: "무엇이든 요청하세요",
    "aria-label": "요청 입력",
  },
} satisfies Meta<typeof Textarea>;

export default meta;

type Story = StoryObj<typeof meta>;

/** 아무 모양도 입히지 않은 기본 상태예요. 직접 입력해 볼 수 있어요. */
export const Default: Story = {};
