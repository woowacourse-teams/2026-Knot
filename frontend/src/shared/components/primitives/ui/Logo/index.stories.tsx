import type { Meta, StoryObj } from "@storybook/react-webpack5";

import { theme } from "@/shared/provider/themeProvider";

import Logo from ".";

/**
 * knot 워드마크 로고예요. 로그인·온보딩처럼 화면 가운데 카드를 둔 화면의 맨 위에 씁니다.
 *
 * - 가로 길이(`width`)만 정하면 세로는 비율에 맞춰 따라와요. 숫자는 `rem`, 문자열은 `113px`처럼 단위까지 그대로 써요.
 * - 색은 기본으로 Neutral/800이에요. 다른 색이 필요하면 `color`를 넘겨요.
 *
 * **왜 컴포넌트로 감쌌나**
 * - 로고 svg를 그대로 쓰면 가로·세로가 같은 값으로 박혀 정사각형으로 찌그러져요. 이를 바로잡는 처리를 화면마다 반복하지 않으려고 감쌌어요.
 */
const meta = {
  title: "Shared/Logo",
  component: Logo,
} satisfies Meta<typeof Logo>;

export default meta;

type Story = StoryObj<typeof meta>;

/** 기본 크기(113px)예요. 예: 로그인·온보딩 화면 맨 위 */
export const Default: Story = {};

/** 가로 길이를 바꿀 때예요. 세로는 비율에 맞춰 따라와요. */
export const CustomWidth: Story = {
  args: { width: 5 },
};

/** 색을 바꿀 때예요. */
export const CustomColor: Story = {
  args: { color: theme.sub.accent[500] },
};
