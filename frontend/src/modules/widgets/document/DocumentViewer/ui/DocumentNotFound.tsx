import styled from "@emotion/styled";
import Button from "@primitives/ui/Button";

interface DocumentNotFoundProps {
  /** `홈으로`를 눌렀을 때 실행할 동작 */
  onGoHome: () => void;
}

/**
 * 문서를 열 수 없을 때(없는 문서 · 권한 없음 · 잘못된 주소) 보여주는 임시 안내.
 *
 * 기획(ERR-R2)은 공통 "잘못된 요청" 화면을 보여주고 홈으로 보내는 것이에요.
 * 그 화면이 아직 없어 문서 영역 안에서 같은 행동(홈으로)을 제공하고, 생기면 그 화면으로 바꿔요.
 */
export default function DocumentNotFound({ onGoHome }: DocumentNotFoundProps) {
  return (
    <Container>
      <Message>문서를 찾을 수 없어요</Message>
      <Button size="md" variant="outline" onClick={onGoHome}>
        홈으로
      </Button>
    </Container>
  );
}

const Container = styled.div`
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 1rem; /* 16px */
  width: 100%;
`;

const Message = styled.p`
  ${({ theme }) => theme.text.body02};
  color: ${({ theme }) => theme.neutral[700]};
  text-align: center;
`;
