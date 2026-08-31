import styled from "@emotion/styled";
import Button from "@primitives/ui/Button";

import EnterWorkspaceIllustration from "@/assets/illustrations/enterWorkspace.svg";

import { useWorkspaceJoin } from "./model/useWorkspaceJoin";

/**
 * 워크스페이스 입장 확인 카드.
 *
 * 초대 코드를 입력했거나 초대 링크를 타고 온 사용자에게 어느 워크스페이스에 합류하는지 보여주고,
 * `참여할게요`를 누르면 현재 `:workspaceId`의 워크스페이스 홈(`/workspace/:workspaceId`)으로 이동해요.
 *
 * 미리보기 API(#243)가 아직 없어 워크스페이스 이름은 임시 상수이고,
 * 참여 API(#244)도 호출하지 않은 채 이동만 합니다.
 *
 * 로고와 중앙 배치는 `CenteredLayout`이 맡으므로 이 카드는 자기 모양만 그려요.
 *
 * @see {@link https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=600-10180 초대 링크로 워크스페이스 입장}
 * @see {@link https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=600-10176 초대 코드 입력/워크스페이스 입장}
 * @see {@link https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=443-801 Card/Onboarding & Workspace}
 */
export default function WorkspaceJoinCard() {
  const { workspaceName, handleJoin } = useWorkspaceJoin();

  return (
    <Container>
      <Illustration />

      <Header>
        <Title>{workspaceName}</Title>
        <Description>초대를 수락하면 함께 기록할 수 있어요</Description>
      </Header>

      <Button size="lg" isFullWidth onClick={handleJoin}>
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
