import styled from "@emotion/styled";
import Row from "@primitives/layout/Row";
import type { Meta, StoryObj } from "@storybook/react-webpack5";

import BackIcon from "@/assets/icons/back.svg";
import ChatListIcon from "@/assets/icons/chatList.svg";
import NewChatIcon from "@/assets/icons/newChat.svg";

import ChatPanelHeaderLayout from ".";

/**
 * 채팅 패널 맨 위의 헤더 자리를 잡는 레이아웃이에요. `<header>`로 그려요.
 *
 * 자식을 왼쪽과 오른쪽 양 끝으로 벌려 놓아요.
 * 왼쪽 영역(로고·제목·뒤로가기)과 오른쪽 영역(액션 아이콘)을 **각각 하나의 요소로 묶어** 넘깁니다.
 * 묶지 않고 셋 이상을 넘기면 사이사이가 똑같이 벌어져 의도와 다르게 놓여요.
 *
 * 배치만 맡으므로 색·간격 같은 안쪽 스타일은 쓰는 쪽에서 정해요.
 *
 * **디자인 원본**: [채팅 패널 헤더](https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=603-2867&t=NtCKbgE8RjHqh556-11)
 */
const meta = {
  title: "Shared/Layout/ChatPanelHeaderLayout",
  component: ChatPanelHeaderLayout,
  args: {
    children: (
      <>
        <Row align="center" gap={0.5}>
          <BackIcon />
          <span>탐색</span>
        </Row>
        <NewChatIcon />
      </>
    ),
  },
  decorators: [
    (Story) => (
      <Frame>
        <Story />
      </Frame>
    ),
  ],
} satisfies Meta<typeof ChatPanelHeaderLayout>;

export default meta;

type Story = StoryObj<typeof meta>;

/** 왼쪽에 뒤로가기와 제목, 오른쪽에 액션 아이콘을 둔 기본 모양이에요. */
export const Default: Story = {};

/** 오른쪽 액션이 여러 개면 하나로 묶어 넘겨요. 묶은 요소 안의 간격은 쓰는 쪽이 정해요. */
export const MultipleActions: Story = {
  args: {
    children: (
      <>
        <Row align="center" gap={0.5}>
          <BackIcon />
          <span>탐색</span>
        </Row>
        <Row align="center" gap={0.75}>
          <ChatListIcon />
          <NewChatIcon />
        </Row>
      </>
    ),
  },
};

const Frame = styled.div`
  width: 24rem;
  padding: 1rem;
  border: 1px dashed ${({ theme }) => theme.neutral[200]};
  color: ${({ theme }) => theme.neutral[800]};
`;
