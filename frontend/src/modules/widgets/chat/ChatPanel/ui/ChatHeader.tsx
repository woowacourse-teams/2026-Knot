import styled from "@emotion/styled";
import ChatPanelHeaderLayout from "@/shared/components/primitives/layout/ChatPanelHeaderLayout";
import Spacing from "@/shared/components/primitives/layout/Spacing";
import Knotted from "@/assets/logos/logoMark.svg";
import NewChat from "@/assets/icons/newChat.svg";

interface ChatHeaderProps {
  onStartNewChat: () => void;
}

/**
 * 대화 화면 상단 헤더.
 *
 * 좌측에 서비스 로고와 이름을, 우측에 새 대화 시작 버튼을 둡니다.
 * 대화 목록은 GNB 좌측의 목록 드로어가 맡으므로 여기에는 두지 않습니다.
 *
 * @see https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=578-1363
 */
export default function ChatHeader({ onStartNewChat }: ChatHeaderProps) {
  return (
    <ChatPanelHeaderLayout>
      <Left>
        <IconWrapper>
          <Knotted color="#FAF9F7" />
        </IconWrapper>

        <Spacing direction="horizontal" size={0.5} />

        <Title>knotted</Title>
      </Left>

      <Right>
        <IconContainer>
          <IconWrapper aria-label="새 대화 시작" onClick={onStartNewChat}>
            <NewChat color="#8C8880" />
          </IconWrapper>
        </IconContainer>
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

const IconContainer = styled.div`
  display: flex;
  align-items: center;
`;
const IconWrapper = styled.button`
  display: flex;
  justify-content: center;
  align-items: center;
  width: 1.25rem;
  height: 1.25rem;
`;
