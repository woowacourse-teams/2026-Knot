import styled from "@emotion/styled";
import type { Meta, StoryObj } from "@storybook/react-webpack5";

import DockColumn from ".";

/**
 * 감싼 영역의 가운데에 놓이는 열이에요. 색·모양은 없고 폭과 위치만 잡아요.
 *
 * 탐색 대화 열과 하단 독이 이 열을 함께 써서, 둘은 늘 같은 폭·같은 위치에 놓여요.
 *
 * **폭 규칙**
 * - 감싼 영역에서 양옆 40px씩을 뺀 폭까지 쓰되, 최대 760px까지만 늘어나요.
 * - 왼쪽에 패널이 고정되면 감싼 영역이 줄어 열도 그만큼 밀리고 좁아져요.
 *   예: 1440 화면에서 패널이 없으면 340~1100, 대화 목록(280)을 고정하면 500~1260
 */
const meta = {
  title: "Shared/Layout/DockColumn",
  component: DockColumn,
  parameters: {
    layout: "fullscreen",
    design: {
      type: "figma",
      url: "https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=2106-28974",
    },
  },
  render: (args) => (
    <DockColumn {...args}>
      <Item>무엇을 찾고 있나요?</Item>
    </DockColumn>
  ),
} satisfies Meta<typeof DockColumn>;

export default meta;

type Story = StoryObj<typeof meta>;

/** 화면을 다 쓰는 영역에 둔 모양이에요. 예: 아무 패널도 고정하지 않은 탐색 화면 */
export const Default: Story = {};

/** 감싼 영역이 좁아 최대 폭보다 줄어든 모양이에요. 예: 대화 목록과 찾은 기록이 함께 열린 탐색 화면 */
export const Narrow: Story = {
  decorators: [
    (Story) => (
      <div style={{ width: "42.5rem" }}>
        <Story />
      </div>
    ),
  ],
};

const Item = styled.div`
  padding: 1rem;
  border-radius: 0.75rem;
  background-color: ${({ theme }) => theme.neutral[100]};
  color: ${({ theme }) => theme.neutral[800]};
  text-align: center;
`;
