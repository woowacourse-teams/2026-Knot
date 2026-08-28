import styled from "@emotion/styled";
import WorkspaceCreateButton from "@features/workspace/WorkspaceCreateButton";
import WorkspaceJoinByCodeButton from "@features/workspace/WorkspaceJoinByCodeButton";
import Divider from "@primitives/ui/Divider";

/**
 * 워크스페이스 생성 및 참여 선택 카드.
 *
 * 가입을 마친 사용자가 새 워크스페이스를 만들지, 초대 코드로 참여할지 고르는 분기점이에요.
 * API 호출 없이 두 선택지를 각각 `/workspace/create`와 `/workspace/code`로 연결합니다.
 *
 * 로고와 중앙 배치는 `CenteredLayout`이 맡으므로 이 카드는 자기 모양만 그려요.
 *
 * @see {@link https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=432-1750 Card/Onboarding/Workspace}
 */
export default function WorkspaceEntryCard() {
  return (
    <Container>
      <Header>
        <Title>워크스페이스 생성 및 참여</Title>
        <Description>
          새 워크스페이스를 만들거나
          <br />
          초대받은 워크스페이스에 참여하세요
        </Description>
      </Header>

      <Actions>
        <WorkspaceCreateButton />
        <Divider label="또는" />
        <WorkspaceJoinByCodeButton />
      </Actions>
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
  gap: 0.75rem;
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

const Actions = styled.div`
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.75rem;
  width: 100%;
`;
