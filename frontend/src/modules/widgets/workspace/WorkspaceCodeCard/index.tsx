import styled from "@emotion/styled";
import Spinner from "@primitives/ui/Spinner";
import TextField from "@primitives/ui/TextField";

import CheckIcon from "@/assets/icons/check.svg";

import { INVITE_CODE_LENGTH } from "./constants/inviteCode";
import { useWorkspaceCode } from "./model/useWorkspaceCode";

const SUCCESS_MESSAGE = "확인됐어요. 곧 다음 단계로 이동해요.";

/**
 * 초대 코드 입력 카드.
 *
 * 동작 규칙은 스토리북 `Workspace/WorkspaceCodeCard`에서 확인해요.
 */
export default function WorkspaceCodeCard() {
  const { inputCode, isVerifying, isVerified, errorMessage, handleChange } =
    useWorkspaceCode();

  const rightComponent = isVerifying ? (
    <Spinner />
  ) : isVerified ? (
    <SuccessIcon />
  ) : null;

  return (
    <Container>
      <Header>
        <Title>초대 코드 입력</Title>
        <Description>팀에서 전달받은 참여 코드를 입력하세요</Description>
      </Header>

      <TextField
        variant="code"
        value={inputCode}
        onChange={handleChange}
        placeholder="코드를 입력하세요"
        maxLength={INVITE_CODE_LENGTH}
        errorMessage={errorMessage}
        successMessage={isVerified ? SUCCESS_MESSAGE : undefined}
        readOnly={isVerifying || isVerified}
        aria-busy={isVerifying}
        rightComponent={rightComponent}
        aria-label="참여 코드"
        autoComplete="off"
        autoCapitalize="characters"
        autoFocus
      />
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

const SuccessIcon = styled(CheckIcon)`
  width: 1.25rem; /* 20px */
  height: 1.25rem;
  color: ${({ theme }) => theme.sub.accent[500]};
`;
