import styled from "@emotion/styled";
import Button from "@primitives/ui/Button";

import EnterWorkspaceIllustration from "@/assets/illustrations/enterWorkspace.svg";

import { useWorkspaceJoin } from "./model/useWorkspaceJoin";

/**
 * 합류할 워크스페이스를 보여 주고 참여를 처리하는 입장 확인 카드.
 *
 * 동작 규칙은 스토리북 `Workspace/WorkspaceJoinCard`에서 확인해요.
 */
export default function WorkspaceJoinCard() {
  const { workspaceName, isPending, handleJoin } = useWorkspaceJoin();

  if (workspaceName === undefined) return null;

  return (
    <Container>
      <Illustration />

      <Header>
        <Title>{workspaceName}</Title>
        <Description>초대를 수락하면 함께 기록할 수 있어요</Description>
      </Header>

      <Button size="lg" isFullWidth isLoading={isPending} onClick={handleJoin}>
        참여할게요
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

const Illustration = styled(EnterWorkspaceIllustration)`
  flex-shrink: 0;
  width: 3rem; /* 48px */
  height: 3rem;
  color: ${({ theme }) => theme.neutral[800]};
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
