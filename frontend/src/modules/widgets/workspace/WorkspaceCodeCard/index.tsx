import styled from "@emotion/styled";
import Spinner from "@primitives/ui/Spinner";
import TextField from "@primitives/ui/TextField";

import { INVITE_CODE_LENGTH } from "./constants/inviteCode";
import { useWorkspaceCode } from "./model/useWorkspaceCode";

/**
 * 초대 코드 입력 카드.
 *
 * 팀에서 전달받은 6자리 참여 코드를 입력하면 별도 버튼 없이 자동으로 검증을 시작해요.
 * 검증 중에는 입력을 잠그고 우측에 스피너를 보여주며, 통과하면 입장 확인 화면으로 넘어가고
 * 실패하면 에러 보더와 `올바르지 않은 코드예요. 다시 확인해 주세요.`를 띄운 뒤 잠금을 풉니다.
 *
 * 입력값은 BE 계약(#243)과 같이 대문자로 표시하고, `maxLength`로 7자째 입력은 막아요.
 * 미리보기 API(#243)가 아직 없어 짧은 지연 뒤 형식 검사로 대신하고,
 * 통과하면 임시 workspaceId로 `/workspace/:workspaceId/join`으로 이동합니다.
 *
 * 로고와 중앙 배치는 `CenteredLayout`이 맡으므로 이 카드는 자기 모양만 그려요.
 *
 * @see {@link https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=600-10168 초대 코드 입력}
 * @see {@link https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=600-10172 초대 코드 입력/입력 에러}
 * @see {@link https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=443-910 Field/TextField/Code}
 */
export default function WorkspaceCodeCard() {
  const { inputCode, isVerifying, errorMessage, handleChange } =
    useWorkspaceCode();

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
        readOnly={isVerifying}
        aria-busy={isVerifying}
        rightComponent={isVerifying && <Spinner />}
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
