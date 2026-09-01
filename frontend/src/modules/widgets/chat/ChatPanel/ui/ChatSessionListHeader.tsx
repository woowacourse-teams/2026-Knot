import styled from "@emotion/styled";
import ChatPanelHeaderLayout from "@/shared/components/primitives/layout/ChatPanelHeaderLayout";
import Spacing from "@/shared/components/primitives/layout/Spacing";
import Back from "@/assets/icons/back.svg";
import NewChat from "@/assets/icons/newChat.svg";

interface ChatSessionListHeaderProps {
  onBack: () => void;
  onStartNewChat: () => void;
}

/**
 * 대화 목록 화면 상단 헤더.
 *
 * 좌측 뒤로가기로 대화 화면으로 돌아가고, 우측에서 새 대화를 시작합니다.
 *
 * @see https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=600-10217
 */
export default function ChatSessionListHeader({
  onBack,
  onStartNewChat,
}: ChatSessionListHeaderProps) {
  return (
    <ChatPanelHeaderLayout>
      <Left>
        <IconWrapper aria-label="대화 화면으로 돌아가기" onClick={onBack}>
          <Back color="#D8D4CD" />
        </IconWrapper>

        <Spacing direction="horizontal" size={0.625} />

        <Title>대화 목록</Title>
      </Left>

      <Right>
        <IconWrapper aria-label="새 대화 시작" onClick={onStartNewChat}>
          <NewChat color="#8C8880" />
        </IconWrapper>
      </Right>
    </ChatPanelHeaderLayout>
  );
}

const Left = styled.div`
  display: flex;
  align-items: center;
`;

const Right = styled.div`
  display: flex;
  align-items: center;
`;

const Title = styled.h2`
  ${({ theme }) => theme.text.body01}
  color: ${({ theme }) => theme.neutral[0]}
`;

const IconWrapper = styled.button`
  display: flex;
  justify-content: center;
  align-items: center;
  width: 1.25rem;
  height: 1.25rem;
  cursor: pointer;
`;
