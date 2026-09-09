import styled from "@emotion/styled";
import Button from "@primitives/ui/Button";

interface DesktopOnlyNoticeProps {
  onGoToWorkspace: () => void;
}

/**
 * 브라우저에서 이 화면을 열었을 때 보여 주는 안내.
 *
 * 내 Claude 구독으로 답하는 건 데스크톱 앱만 할 수 있어요. 브라우저의 질문은 늘 서버 모델이 답해요.
 */
export default function DesktopOnlyNotice({
  onGoToWorkspace,
}: DesktopOnlyNoticeProps) {
  return (
    <Container>
      <Header>
        <Title>데스크톱 앱에서 이어가요</Title>
        <Description>
          내 Claude 구독으로 답하는 건 Knot 데스크톱 앱에서만 할 수 있어요.
          <br />
          브라우저의 질문은 서버 모델이 답해요.
        </Description>
      </Header>

      <Button size="lg" variant="outline" isFullWidth onClick={onGoToWorkspace}>
        워크스페이스로 이동
      </Button>
    </Container>
  );
}

const Container = styled.section`
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 1.75rem; /* 28px */
  width: 100%;
  max-width: 28.75rem; /* 460px */
  padding: 3rem; /* 48px */
  border-radius: 1.5rem; /* 24px */
  background-color: ${({ theme }) => theme.neutral[0]};
  box-shadow: ${({ theme }) => theme.shadow02};
`;

const Header = styled.div`
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.75rem; /* 12px */
  text-align: center;
  overflow-wrap: break-word;
`;

const Title = styled.h1`
  color: ${({ theme }) => theme.neutral[900]};
  ${({ theme }) => theme.text.heading02};
`;

const Description = styled.p`
  color: ${({ theme }) => theme.neutral[600]};
  ${({ theme }) => theme.text.body01};
`;
