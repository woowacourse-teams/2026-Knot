import type { Meta, StoryObj } from "@storybook/react-webpack5";

import SearchConversation from ".";

/**
 * 탐색 화면 가운데의 대화 칸이에요. 지금 대화가 어떤 상태인지에 따라 칸에 무엇을 그릴지 정해요.
 *
 * **동작 규칙**
 * - 질문이 아직 없는 빈 대화에서는 「무엇을 찾고 있나요?」 안내를 칸 가운데에 띄워요.
 * - 빈 대화는 저장하지 않고, 첫 질문을 받은 뒤에야 대화가 돼요.
 */
const meta = {
  title: "Search/SearchConversation",
  component: SearchConversation,
  parameters: {
    layout: "fullscreen",
    design: {
      type: "figma",
      url: "https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=2098-29235",
    },
  },
  decorators: [
    (Story) => (
      // 대화 칸은 화면 높이를 채우므로 탐색 화면과 비슷한 높이를 줘요
      <div style={{ height: "40rem" }}>
        <Story />
      </div>
    ),
  ],
} satisfies Meta<typeof SearchConversation>;

export default meta;

type Story = StoryObj<typeof meta>;

/** 새 채팅을 눌러 질문 없이 탐색 화면에 들어온 상태예요. */
export const Default: Story = {};
