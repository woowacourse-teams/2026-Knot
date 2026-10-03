import { AUTH_ME_API_PATH } from "@api/fetch/api/v1/auth/me";
import { meResponse } from "@api/mock/responses/auth";
import type { Meta, StoryObj } from "@storybook/react-webpack5";
import { delay, http, HttpResponse } from "msw";

import MemberProfileAvatar from ".";

const BROKEN_PROFILE_IMAGE_URL = "https://avatars.githubusercontent.com/u/0";

/**
 * 로그인한 회원의 프로필 아바타예요. GNB 오른쪽에 놓여요.
 *
 * **동작 규칙**
 * - GitHub 로그인으로 받아 둔 프로필 이미지를 그려요.
 * - 이미지를 불러오지 못하면 닉네임 첫 글자로 대신해요.
 * - 회원 정보를 받아 오기 전에는 닉네임도 없어 기본 사람 모양을 그려요.
 * - 지금은 보여 주기만 하고 누르는 동작은 없어요.
 *
 * **디자인 원본**: [Avatar size=32](https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=587-516)
 */
const meta = {
  title: "Member/MemberProfileAvatar",
  component: MemberProfileAvatar,
  parameters: { layout: "centered" },
} satisfies Meta<typeof MemberProfileAvatar>;

export default meta;

type Story = StoryObj<typeof meta>;

/** GitHub 프로필 이미지가 있는 회원이에요. */
export const Default: Story = {};

/** 프로필 이미지를 불러오지 못했을 때예요. 예: GitHub 이미지 서버가 응답하지 않을 때. 닉네임 첫 글자로 대신해요. */
export const ProfileImageFailed: Story = {
  parameters: {
    msw: {
      handlers: {
        // 앞 스토리에서 받은 이미지가 브라우저 캐시로 그려지지 않도록 다른 주소를 줘요
        me: http.get(`*${AUTH_ME_API_PATH}`, () =>
          HttpResponse.json({
            ...meResponse,
            profileImageUrl: BROKEN_PROFILE_IMAGE_URL,
          }),
        ),
        profileImage: http.get(
          BROKEN_PROFILE_IMAGE_URL,
          () => new HttpResponse(null, { status: 404 }),
        ),
      },
    },
  },
};

/** 회원 정보를 받아 오는 중이에요. 기본 사람 모양이 보여요. */
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
