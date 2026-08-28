import styled from "@emotion/styled";
import useNavigateToWorkspaceInvite from "@hooks/domain/workspace/useNavigateToWorkspaceInvite";
import Button from "@primitives/ui/Button";
import TextField from "@primitives/ui/TextField";
import { type ChangeEvent, type FormEvent, useState } from "react";

import { WORKSPACE_NAME_MAX_LENGTH } from "./constants/workspaceName";
import { getWorkspaceNameErrorMessage } from "./utils/getWorkspaceNameErrorMessage";

/**
 * 새 워크스페이스 이름 입력 카드.
 *
 * 입력 전 · 입력 중 · 입력 에러 세 상태를 한 카드에서 다뤄요.
 * 글자를 칠 때마다 허용 문자(한글·영어·공백)를 검사해 바로 에러 문구를 띄우고,
 * `maxLength`로 21자째 입력은 막습니다.
 *
 * 값이 비었거나(공백만 있는 값 포함) 에러면 버튼이 비활성이고, 라벨은 늘 `워크스페이스 생성`이에요.
 * 생성 API(#216)가 아직 없어 유효한 이름으로 제출하면 임시 workspaceId로 초대 화면으로 넘어갑니다.
 *
 * 로고와 중앙 배치는 `CenteredLayout`이 맡으므로 이 카드는 자기 모양만 그려요.
 *
 * @see {@link https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=431-1294 새 워크스페이스 생성/입력 전}
 * @see {@link https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=432-1576 새 워크스페이스 생성/입력 중}
 * @see {@link https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=432-1594 새 워크스페이스 생성/입력 에러}
 */
export default function CreateWorkspaceCard() {
  const [name, setName] = useState("");
  const { navigateToWorkspaceInvite } = useNavigateToWorkspaceInvite();

  const errorMessage = getWorkspaceNameErrorMessage(name);
  const isSubmittable = name.trim().length > 0 && !errorMessage;

  const handleChange = (event: ChangeEvent<HTMLInputElement>) => {
    setName(event.target.value.slice(0, WORKSPACE_NAME_MAX_LENGTH));
  };

  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!isSubmittable) return;

    // TODO(#216): 워크스페이스 생성 API 연결 후 응답의 workspaceId로 교체
    const TEMP_WORKSPACE_ID = "temp";
    navigateToWorkspaceInvite(TEMP_WORKSPACE_ID);
  };

  return (
    <Root>
      <Title>워크스페이스 이름을 입력하세요</Title>

      <Form onSubmit={handleSubmit}>
        <Field>
          <Counter>
            {name.length}/{WORKSPACE_NAME_MAX_LENGTH}
          </Counter>
          <TextField
            value={name}
            onChange={handleChange}
            placeholder="예시: knot"
            maxLength={WORKSPACE_NAME_MAX_LENGTH}
            errorMessage={errorMessage}
            aria-label="워크스페이스 이름"
            autoComplete="off"
            autoFocus
          />
        </Field>

        <Button type="submit" size="lg" isFullWidth disabled={!isSubmittable}>
          워크스페이스 생성
        </Button>
      </Form>
    </Root>
  );
}

const Root = styled.section`
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 0.75rem; /* 12px */
  width: 100%;
  max-width: 28.75rem; /* 460px */
  padding: 3rem; /* 48px */
  border-radius: 1.5rem; /* 24px */
  background-color: ${({ theme }) => theme.neutral[0]};
  box-shadow: 0 12px 32px 0 rgba(15, 23, 41, 0.08);
`;

const Title = styled.h1`
  color: ${({ theme }) => theme.neutral[900]};
  overflow-wrap: break-word;
  ${({ theme }) => theme.text.heading02};
`;

const Form = styled.form`
  display: flex;
  flex-direction: column;
  gap: 1.5rem; /* 24px */
  width: 100%;
`;

const Field = styled.div`
  display: flex;
  flex-direction: column;
  gap: 0.5rem; /* 8px */
  width: 100%;
`;

const Counter = styled.p`
  width: 100%;
  color: ${({ theme }) => theme.neutral[600]};
  text-align: right;
  ${({ theme }) => theme.text.caption01};
`;
