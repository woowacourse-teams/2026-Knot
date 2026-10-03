import type { Meta, StoryObj } from "@storybook/react-webpack5";

import ChatBubble from ".";

/**
 * 사용자가 보낸 말풍선이에요. 탐색 화면의 대화에서 사용자가 보낸 질문을 보여줄 때 씁니다.
 *
 * - 너비는 내용에 맞춰 늘어나되 630px에서 멈춰요.
 * - 입력창에서 넣은 줄바꿈을 그대로 보여줘요.
 * - 긴 URL처럼 끊을 곳이 없는 글자도 말풍선을 뚫고 나가지 않고 줄을 바꿔요.
 */
const meta = {
  title: "Shared/ChatBubble",
  component: ChatBubble,
  parameters: {
    design: {
      type: "figma",
      url: "https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=1209-807",
    },
  },
  args: {
    children: "DB 기술 선정 관련해서 정리된 문서 있어?",
  },
} satisfies Meta<typeof ChatBubble>;

export default meta;

type Story = StoryObj<typeof meta>;

/** 한 줄 질문이에요. 너비가 내용만큼만 늘어나요. */
export const Default: Story = {};

/** 입력창에서 줄을 바꿔 보낸 질문이에요. 줄바꿈이 그대로 보여요. */
export const MultiLine: Story = {
  args: {
    children:
      "DB 기술 선정 관련해서 정리된 문서 있어?\n그럼 초기 스키마는 어디에 정리돼 있어?",
  },
};

/** 630px을 넘는 긴 질문이에요. 630px에서 멈추고 줄을 바꿔요. 긴 URL도 말풍선 안에서 끊겨요. */
export const LongText: Story = {
  args: {
    children:
      "이 노션 문서에 정리된 내용 요약해 줘 https://www.notion.com/ko/product?utm_source=google&utm_medium=cpc&utm_campaign=brand%5Fkeyword%5Fgroup&utm_term=notion&utm_content=All",
  },
};
