import styled from "@emotion/styled";
import type { Meta, StoryObj } from "@storybook/react-webpack5";

import Spacing from ".";

/**
 * 형제 요소 사이에 빈 간격을 두는 레이아웃이에요. 색·모양 같은 실체는 없고 자리만 차지해요.
 *
 * 요소 자체의 여백(`margin`)을 건드리지 않고 간격을 주고 싶을 때, 간격을 벌릴 두 형제 사이에 놓아 씁니다.
 * 자식은 받지 않아요. 같은 간격이 반복되면 `Shared/Layout/Stack`의 `gap`이 낫고,
 * 간격이 자리마다 다를 때 `Spacing`을 써요.
 *
 * **방향과 값**
 * - `direction` : `vertical`(기본값)은 위아래, `horizontal`은 좌우로 벌려요.
 * - `size` : 숫자를 넘기면 `rem`으로 붙고, 문자열은 `16px`, `8%`처럼 단위까지 그대로 적용돼요.
 *
 * 아래 스토리에서는 간격이 보이도록 점선으로 칠했어요. 실제로는 보이지 않아요.
 */
const meta = {
  title: "Shared/Layout/Spacing",
  component: Spacing,
  args: { direction: "vertical", size: 1 },
  argTypes: {
    direction: { control: "inline-radio", options: ["vertical", "horizontal"] },
    size: { control: "number" },
  },
  render: (args) => (
    <div
      style={{
        display: "flex",
        flexDirection: args.direction === "horizontal" ? "row" : "column",
        alignItems: "flex-start",
      }}
    >
      <Item>워크스페이스 이름</Item>
      <VisibleSpacing {...args} />
      <Item>팀원과 함께 쓸 이름을 정해 주세요</Item>
    </div>
  ),
} satisfies Meta<typeof Spacing>;

export default meta;

type Story = StoryObj<typeof meta>;

/** 위아래 요소 사이를 벌릴 때 써요. 예: 제목과 설명 사이 */
export const Vertical: Story = {};

/** 좌우 요소 사이를 벌릴 때 써요. 예: 탐색 결과 카드의 아이콘과 제목 사이 */
export const Horizontal: Story = {
  args: { direction: "horizontal", size: 0.625 },
};

/** 피그마 px 값을 그대로 옮길 때 단위까지 문자열로 넘겨요. */
export const SizeWithUnit: Story = {
  args: { size: "40px" },
};

const Item = styled.div`
  padding: 0.5rem 0.75rem;
  border: 1px solid ${({ theme }) => theme.neutral[200]};
  border-radius: 0.5rem;
  color: ${({ theme }) => theme.neutral[800]};
`;

/** 간격이 눈에 보이도록 칠한 Spacing. 스토리에서만 써요 */
const VisibleSpacing = styled(Spacing)`
  outline: 1px dashed ${({ theme }) => theme.sub.accent[500]};
`;
