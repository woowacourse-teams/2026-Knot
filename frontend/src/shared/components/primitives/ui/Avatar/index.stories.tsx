import type { Meta, StoryObj } from "@storybook/react-webpack5";

import Avatar from ".";

/**
 * 사람이나 워크스페이스를 나타내는 원형 아바타예요. GNB의 내 프로필, 사이드바의 워크스페이스 옆에 씁니다.
 *
 * **무엇을 그리나**
 * - 프로필 이미지 → 이름 첫 글자 → 기본 글리프 순으로, 그릴 수 있는 것을 그려요.
 * - 이미지 주소를 받았더라도 불러오기에 실패하면 첫 글자로 내려가요. GitHub 프로필 이미지가 없거나 주소가 깨져도 빈 원이 남지 않게 하려는 거예요.
 *
 * **크기**
 * - 사이드바 워크스페이스는 24, GNB 내 프로필은 32를 써요. 24에서는 첫 글자도 한 단계 작은 글자로 그려요.
 *
 * **디자인 원본**: [Avatar](https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=587-516)
 */
const meta = {
  title: "Shared/Avatar",
  component: Avatar,
  args: {
    label: "내 프로필",
    name: "흑곰",
    size: 32,
  },
  argTypes: {
    size: { control: "inline-radio", options: [24, 32] },
  },
} satisfies Meta<typeof Avatar>;

export default meta;

type Story = StoryObj<typeof meta>;

/** 프로필 이미지가 있을 때예요. 예: GitHub 프로필 이미지가 있는 사용자의 GNB */
export const WithImage: Story = {
  args: { src: "https://avatars.githubusercontent.com/u/9919?v=4" },
};

/** 이미지가 없어 이름 첫 글자로 그릴 때예요. 예: 프로필 이미지가 없는 사용자 */
export const Initial: Story = {};

/** 이미지 주소가 깨져 불러오지 못했을 때예요. 빈 원 대신 첫 글자로 내려가요. */
export const BrokenImage: Story = {
  args: { src: "https://avatars.githubusercontent.com/u/0-not-found" },
};

/** 이미지도 이름도 없을 때예요. 기본 글리프를 그려요. 예: 내 정보를 아직 불러오지 못한 GNB */
export const Glyph: Story = {
  args: { name: undefined },
};

/** 사이드바 워크스페이스(24)와 GNB 내 프로필(32)을 나란히 비교해요. */
export const Sizes: Story = {
  render: (args) => (
    <div style={{ display: "flex", gap: "1rem", alignItems: "center" }}>
      <Avatar {...args} label="knot" name="knot" size={24} />
      <Avatar {...args} size={32} />
    </div>
  ),
};
