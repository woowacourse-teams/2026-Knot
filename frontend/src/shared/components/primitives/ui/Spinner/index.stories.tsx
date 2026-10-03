import type { Meta, StoryObj } from "@storybook/react-webpack5";

import { theme } from "@/shared/provider/themeProvider";

import Spinner from ".";

/**
 * 회전하는 로딩 표시예요. 버튼·입력창 안처럼 작은 자리에서 처리 중임을 보여줄 때 씁니다.
 * 예: 초대 코드를 확인하는 동안 입력창 오른쪽
 *
 * - 색은 부모의 글자색(`color`)을 따라가요. 진한 버튼 위에서는 흰색, 밝은 버튼 위에서는 어두운 색으로 알아서 그려져요.
 * - 지름은 `size`로 정해요. 부모 글자 크기를 따라가게 하려면 `1em`을 넘겨요.
 * - 위치는 잡지 않아요. 어디에 놓을지는 쓰는 쪽이 정해요.
 *
 * **접근성**
 * - 장식이라 스크린리더가 읽지 않아요. 로딩 중이라는 사실은 부모가 `aria-busy`로 알려요.
 * - 화면이나 구획 전체를 기다릴 때는 스크린리더에도 알리는 `LoadingIndicator`를 써요.
 *
 * **디자인 원본**: [loading spinner](https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=422-263)
 */
const meta = {
  title: "Shared/Spinner",
  component: Spinner,
} satisfies Meta<typeof Spinner>;

export default meta;

type Story = StoryObj<typeof meta>;

/** 기본 지름(24px)이에요. */
export const Default: Story = {};

/** 부모 글자색을 따라가는 모습이에요. 진한 배경 위에서는 흰색으로 그려요. 예: 로딩 중인 기본 버튼 */
export const OnDark: Story = {
  decorators: [
    (Story) => (
      <div
        style={{
          display: "inline-flex",
          padding: "1rem",
          borderRadius: "0.875rem",
          backgroundColor: theme.primary,
          color: theme.neutral[0],
        }}
      >
        <Story />
      </div>
    ),
  ],
};

/** 부모 글자 크기를 따라가게 할 때예요. `size="1em"`을 넘겨요. */
export const FollowFontSize: Story = {
  args: { size: "1em" },
  decorators: [
    (Story) => (
      <div style={{ fontSize: "2rem" }}>
        <Story />
      </div>
    ),
  ],
};
