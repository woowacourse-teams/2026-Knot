import type { Meta, StoryObj } from "@storybook/react-webpack5";

import Skeleton from ".";

/**
 * 아직 오지 않은 내용의 자리를 대신 채워 두는 회색 덩어리예요. 목록이나 글을 불러오는 동안 그 자리에 씁니다.
 *
 * - 빈 화면 대신 곧 들어올 내용의 모양을 미리 잡아 둬서, 내용이 도착해도 화면이 덜 튀어요.
 * - 덩어리 하나만 그려요. 무엇이 몇 줄 들어올지는 쓰는 쪽이 정해 여러 개를 놓아요.
 * - 가로는 숫자면 `rem`, 문자열이면 그대로 써요. 세로·둥글기는 `rem` 숫자예요. 기본은 글줄 높이(10px)의 알약 모양이에요.
 *
 * **움직임**
 * - 배경이 은은하게 밝아졌다 어두워지기만 하고, 번쩍이는 빛줄기는 넣지 않았어요. 종이 같은 배색을 쓰는 화면이라 강한 반짝임은 튀기 때문이에요.
 * - 사용자가 기기에서 움직임 줄이기를 켜면 움직이지 않아요.
 */
const meta = {
  title: "Shared/Skeleton",
  component: Skeleton,
  decorators: [
    (Story) => (
      <div style={{ width: "22.5rem" }}>
        <Story />
      </div>
    ),
  ],
} satisfies Meta<typeof Skeleton>;

export default meta;

type Story = StoryObj<typeof meta>;

/** 글 한 줄 자리예요. 부모 너비를 꽉 채워요. */
export const Default: Story = {};

/** 크기와 둥글기를 바꿔 다른 모양의 자리를 잡을 때예요. 예: 32px 아바타 자리 */
export const CustomShape: Story = {
  args: { width: 2, height: 2, radius: 1 },
};

/** 여러 줄 글 자리를 잡는 예예요. 줄 수와 길이는 쓰는 쪽이 정해요. */
export const Paragraph: Story = {
  render: (args) => (
    <div style={{ display: "flex", flexDirection: "column", gap: "0.5rem" }}>
      <Skeleton {...args} />
      <Skeleton {...args} />
      <Skeleton {...args} width="60%" />
    </div>
  ),
};
