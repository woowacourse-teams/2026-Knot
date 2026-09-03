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
 * 팀에서 전달받은 6자리 참여 코드를 입력하면 별도 버튼 없이 미리보기 조회(`GET /invitations/{code}`)를 시작해요.
 * 조회 중에는 입력을 잠그고 우측에 스피너를 보여줘요. 통과하면 우측 체크와
 * `확인됐어요. 곧 다음 단계로 이동해요.`를 1.5초 동안 보여준 뒤 응답의 workspaceId로
 * 입장 확인 화면(`/workspace/:workspaceId/join`)에 넘어가고, 입력한 코드와 워크스페이스 이름은 라우터 state로 넘겨요.
 * 실패하면 에러 보더와 함께 404는 `올바르지 않은 코드예요…`, 429는 `요청이 너무 많아요…`,
 * 그 외는 `코드를 확인하지 못했어요…`를 같은 자리에 띄운 뒤 잠금을 풉니다.
 *
 * 입력값은 BE 계약(ADR 243)과 같이 대문자로 표시하고, `maxLength`로 7자째 입력은 막아요.
 *
 * 로고와 중앙 배치는 `CenteredLayout`이 맡으므로 이 카드는 자기 모양만 그려요.
 *
 * @see {@link https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=600-10168 초대 코드 입력}
 * @see {@link https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=600-10172 초대 코드 입력/입력 에러}
 * @see {@link https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=443-910 Field/TextField/Code}
 * @see {@link https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=664-552 Field/TextField/Code status=인증 완료}
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
