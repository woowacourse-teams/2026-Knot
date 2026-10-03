import type { Meta, StoryObj } from "@storybook/react-webpack5";

import MemberProfileAvatar from ".";

/**
 * 로그인한 회원의 프로필 아바타예요. GNB 오른쪽에 놓여요.
 *
 * **동작 규칙**
 * - GitHub 로그인으로 받아 둔 프로필 이미지를 그려요.
 * - 이미지를 불러오지 못하면 닉네임 첫 글자로 대신해요.
 * - 회원 정보를 받아 오기 전에는 닉네임도 없어 기본 사람 모양을 그려요.
 * - 지금은 보여 주기만 하고 누르는 동작은 없어요.
 */
const meta = {
  title: "Member/MemberProfileAvatar",
  component: MemberProfileAvatar,
  parameters: {
    layout: "centered",
    design: {
      type: "figma",
      url: "https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=587-516",
    },
  },
} satisfies Meta<typeof MemberProfileAvatar>;

export default meta;

type Story = StoryObj<typeof meta>;

/** GitHub 프로필 이미지가 있는 회원이에요. */
export const Default: Story = {};
