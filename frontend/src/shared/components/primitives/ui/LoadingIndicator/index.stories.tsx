import type { Meta, StoryObj } from "@storybook/react-webpack5";

import LoadingIndicator from ".";

/**
 * 무언가를 기다리는 중임을 알리는 표시예요. 화면이나 구획 전체가 데이터를 기다릴 때 씁니다.
 * 예: 로그인 상태를 확인하는 동안의 빈 화면
 *
 * - 스피너만 보이지만 스크린리더에는 `label` 문구로 기다리는 중임을 알려요. 스피너는 장식이라 스크린리더가 읽지 않기 때문이에요.
 * - 부모가 준 자리의 가운데에 놓이고, 그 자리의 크기는 부모가 정해요.
 * - 색은 부모의 글자색(`color`)을 따라가요.
 * - 버튼 안의 로딩은 `Button`의 `isLoading`을 써요.
 */
const meta = {
  title: "Shared/LoadingIndicator",
  component: LoadingIndicator,
  args: {
    label: "로그인 상태를 확인하고 있어요",
  },
} satisfies Meta<typeof LoadingIndicator>;

export default meta;

type Story = StoryObj<typeof meta>;

/** 기본 크기예요. 예: 로그인 상태를 확인하는 동안 */
export const Default: Story = {};

/** 스피너 지름을 바꿀 때예요. */
export const CustomSize: Story = {
  args: { size: "2.5rem", label: "화면을 준비하고 있어요" },
};
