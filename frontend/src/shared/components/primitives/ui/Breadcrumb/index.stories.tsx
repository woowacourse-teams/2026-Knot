import type { Meta, StoryObj } from "@storybook/react-webpack5";

import Breadcrumb from ".";

/**
 * 지금 보고 있는 화면이 어디인지 보여주는 2단 경로예요. 문서 화면 위쪽에 씁니다.
 *
 * - `문서 › 회원 탈퇴 정책`처럼 위 단계와 지금 항목만 그려요.
 * - 위치를 보여주기만 하므로 위 단계를 눌러도 이동하지 않아요.
 * - 지금 항목은 놓인 자리의 폭을 넘으면 한 줄에서 말줄임돼요. 위 단계는 줄어들지 않아요.
 *
 * **디자인 원본**: [Doc/Breadcrumb](https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=1814-16211)
 */
const meta = {
  title: "Shared/Breadcrumb",
  component: Breadcrumb,
  args: {
    parent: "문서",
    current: "회원 탈퇴 정책",
  },
} satisfies Meta<typeof Breadcrumb>;

export default meta;

type Story = StoryObj<typeof meta>;

/** 기본 모양이에요. 예: 문서 화면의 `문서 › 회원 탈퇴 정책` */
export const Default: Story = {};

/** 지금 항목이 놓인 자리보다 길 때예요. 지금 항목만 한 줄에서 말줄임돼요. */
export const LongCurrent: Story = {
  args: {
    current:
      "2026 H2 제품 로드맵 확정 및 DB 기술 선정 관련 논의 회의록",
  },
  decorators: [
    (Story) => (
      <div style={{ width: "15rem" }}>
        <Story />
      </div>
    ),
  ],
};
