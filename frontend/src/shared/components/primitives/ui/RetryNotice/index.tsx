import styled from "@emotion/styled";
import Button from "@primitives/ui/Button";

interface RetryNoticeProps {
  /** 무엇이 안 됐는지 알리는 문구 */
  message: string;
  /** `다시 시도`를 눌렀을 때 실행할 동작 */
  onRetry: () => void;
}

/**
 * 잠깐의 실패를 알리고 다시 시도하게 하는 안내.
 *
 * 동작 규칙은 스토리북 `Shared/RetryNotice`에서 확인해요.
 */
export default function RetryNotice({ message, onRetry }: RetryNoticeProps) {
  return (
    <Container role="alert">
      <Message>{message}</Message>
      <Button size="md" variant="outline" onClick={onRetry}>
        다시 시도
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
  overflow-wrap: break-word;
`;
