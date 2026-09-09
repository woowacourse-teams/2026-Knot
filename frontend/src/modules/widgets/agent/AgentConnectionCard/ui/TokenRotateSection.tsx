import styled from "@emotion/styled";
import Button from "@primitives/ui/Button";

import { AGENT_CONNECTION_MESSAGE } from "../constants/agentConnection";

interface TokenRotateSectionProps {
  isConfirming: boolean;
  isRotating: boolean;
  errorMessage?: string;
  onRequest: () => void;
  onConfirm: () => void;
  onCancel: () => void;
}

/**
 * 연결 토큰 재발급.
 *
 * 재발급은 기존 CLI 등록을 전부 무효로 만들어 되돌릴 수 없으니, 브라우저 확인창 대신
 * 화면 안에서 한 번 더 묻고 나서 실행해요.
 */
export default function TokenRotateSection({
  isConfirming,
  isRotating,
  errorMessage,
  onRequest,
  onConfirm,
  onCancel,
}: TokenRotateSectionProps) {
  return (
    <Container>
      <TextGroup>
        <Title>연결 토큰</Title>
        <Description>
          토큰이 새어 나갔다고 의심되면 다시 발급해요. 등록 명령에 들어가는
          값이라 재발급 뒤에는 다시 복사해 등록해야 해요.
        </Description>
      </TextGroup>

      {isConfirming ? (
        <Confirm role="group" aria-label="연결 토큰 재발급 확인">
          <ConfirmMessage>
            {AGENT_CONNECTION_MESSAGE.rotateConfirm}
          </ConfirmMessage>
          <ConfirmActions>
            <Button size="sm" isLoading={isRotating} onClick={onConfirm}>
              재발급
            </Button>
            <Button
              size="sm"
              variant="outline"
              disabled={isRotating}
              onClick={onCancel}
            >
              취소
            </Button>
          </ConfirmActions>
        </Confirm>
      ) : (
        <Actions>
          <Button size="sm" variant="outline" onClick={onRequest}>
            연결 토큰 재발급
          </Button>
        </Actions>
      )}

      {errorMessage && <ErrorMessage role="alert">{errorMessage}</ErrorMessage>}
    </Container>
  );
}

const Container = styled.div`
  display: flex;
  flex-direction: column;
  gap: 0.75rem; /* 12px */
  width: 100%;
`;

const TextGroup = styled.div`
  display: flex;
  flex-direction: column;
  gap: 0.25rem; /* 4px */
`;

const Title = styled.h3`
  ${({ theme }) => theme.text.label01};
  color: ${({ theme }) => theme.neutral[900]};
`;

const Description = styled.p`
  ${({ theme }) => theme.text.caption02};
  color: ${({ theme }) => theme.neutral[600]};
`;

const Actions = styled.div`
  display: flex;
`;

const Confirm = styled.div`
  display: flex;
  flex-direction: column;
  gap: 0.75rem; /* 12px */
  padding: 1rem; /* 16px */
  border: 1px solid ${({ theme }) => theme.sub.warning[200]};
  border-radius: 0.75rem; /* 12px */
  background-color: ${({ theme }) => theme.sub.warning[50]};
`;

const ConfirmMessage = styled.p`
  ${({ theme }) => theme.text.caption02};
  color: ${({ theme }) => theme.sub.warning[800]};
`;

const ConfirmActions = styled.div`
  display: flex;
  gap: 0.5rem; /* 8px */
`;

const ErrorMessage = styled.p`
  ${({ theme }) => theme.text.caption02};
  color: ${({ theme }) => theme.sub.warning[800]};
`;
