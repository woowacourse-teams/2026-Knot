import { AUTH_ME_API_PATH } from "@api/fetch/api/v1/auth/me";
import { meResponse } from "@api/mock/responses/auth";
import type { Meta, StoryObj } from "@storybook/react-webpack5";
import { delay, http, HttpResponse } from "msw";

import MemberGreeting from ".";

/**
 * 워크스페이스 홈 상단의 인사말이에요.
 *
 * **동작 규칙**
 * - 닉네임은 로그인한 회원 정보에서 와요. 정보가 오기 전에는 `반가워요`만 보여 자리를 지키고, 닉네임이 오면 `반가워요, {닉네임} 님`으로 채워요.
 * - 회원 정보를 못 받아도 여기서는 따로 안내하지 않아요. 로그인이 풀렸다면 같은 화면의 워크스페이스 조회도 실패해 로그인 화면으로 옮겨 가기 때문이에요.
 */
const meta = {
  title: "Member/MemberGreeting",
  component: MemberGreeting,
  parameters: {
    layout: "centered",
    design: {
      type: "figma",
      url: "https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=600-10083",
    },
  },
} satisfies Meta<typeof MemberGreeting>;

export default meta;

type Story = StoryObj<typeof meta>;

/** 회원 정보를 받아 닉네임까지 채운 인사말이에요. */
export const Default: Story = {};

/** 회원 정보를 받아 오는 중이에요. 닉네임 없이 인사만 보여요. */
export const Loading: Story = {
  parameters: {
    msw: {
      handlers: {
        me: http.get(`*${AUTH_ME_API_PATH}`, async () => {
          await delay("infinite");
          return HttpResponse.json(meResponse);
        }),
      },
    },
  },
};
