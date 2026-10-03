import type { Meta, StoryObj } from "@storybook/react-webpack5";
import { fn } from "storybook/test";

import Spacing from "@primitives/layout/Spacing";
import Button from "@primitives/ui/Button";
import CountTextField from "@primitives/ui/CountTextField";

import OnboardingCard from ".";

/**
 * 온보딩 화면에서 내용을 담는 흰 카드예요. 닉네임 입력처럼 가운데 카드 하나로 이뤄진 온보딩 화면에 씁니다.
 *
 * **크기**
 * - 너비를 고정하지 않고 최대 너비만 둬요. 넓은 화면에서는 456px에서 멈추고, 좁아지면 화면을 따라 줄어들어요.
 * - 안쪽 내용 폭은 좌우 여백 48px을 뺀 360px이에요.
 * - 피그마 원본은 460px이지만 여백 48과 내용 360을 더하면 456이에요. 남는 4px은 내용이 바뀌면 따라 바뀌어야 하는 값이라 보고 456으로 두었어요.
 *
 * **쓰는 법**
 * - 자식 사이 간격은 자리마다 달라서(12, 24) 카드가 정하지 않아요. 쓰는 쪽에서 `Spacing`으로 벌려요.
 * - 홈 화면 카드와는 여백·간격이 달라서(48/12 vs 20/32) 같은 컴포넌트로 묶지 않았어요.
 *
 * **디자인 원본**: [Card/Onboarding & Workspace](https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=424-1193)
 */
const meta = {
  title: "Shared/OnboardingCard",
  component: OnboardingCard,
  args: {
    children: (
      <>
        <h1>닉네임</h1>
        <Spacing size={0.75} />
        <CountTextField
          value=""
          onChange={fn()}
          maxLength={20}
          placeholder="닉네임"
          aria-label="닉네임"
        />
        <Spacing size={1.5} />
        <Button size="lg" isFullWidth disabled>
          확인
        </Button>
      </>
    ),
  },
} satisfies Meta<typeof OnboardingCard>;

export default meta;

type Story = StoryObj<typeof meta>;

/** 넓은 화면에서의 모양이에요. 456px에서 멈춰요. 예: 닉네임 입력 화면 */
export const Default: Story = {};

/** 카드보다 좁은 화면이에요. 카드가 화면을 따라 줄어들어요. */
export const Narrow: Story = {
  decorators: [
    (Story) => (
      <div style={{ width: "20rem" }}>
        <Story />
      </div>
    ),
  ],
};
