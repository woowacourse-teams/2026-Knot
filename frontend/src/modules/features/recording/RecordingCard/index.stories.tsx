import type { Meta, StoryObj } from "@storybook/react-webpack5";

import RecordingCard from ".";

/**
 * 녹음 화면(`/workspace/:workspaceId/recording`)에서 녹음 조작 바 아래에 놓는 카드예요.
 *
 * **무엇을 보여 주나**
 * - 제목 자리에 회의 제목 대신 `{시작한 사람} 님의 녹음`을 보여 줘요. 문서 제목은 녹음이 끝난 뒤 주제별로 자동으로 붙기 때문이에요.
 * - 아래에는 녹음이 어떻게 이어지는지 안내 두 줄을 둬요. 다른 화면으로 옮겨 가도 녹음이 계속되고, 끝내면 문서로 정리된다는 내용입니다.
 *
 * **동작 규칙**
 * - 닉네임은 로그인한 회원 정보(`GET /auth/me`)에서 올 예정이에요. 아직 연결 전이라 지금은 임시 이름이 보입니다.
 */
const meta = {
  title: "Recording/RecordingCard",
  component: RecordingCard,
  parameters: {
    design: {
      type: "figma",
      url: "https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=2241-530",
    },
  },
  decorators: [
    // 녹음 화면의 가운데 열 너비(최대 960px)에 맞춰요
    (Story) => (
      <div style={{ width: "60rem" }}>
        <Story />
      </div>
    ),
  ],
} satisfies Meta<typeof RecordingCard>;

export default meta;

type Story = StoryObj<typeof meta>;

/** 녹음이 진행 중일 때 녹음 화면에서 보이는 모습이에요. */
export const Default: Story = {};
