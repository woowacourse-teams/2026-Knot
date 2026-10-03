import styled from "@emotion/styled";
import Button from "@primitives/ui/Button";
import TextField from "@primitives/ui/TextField";

import { WORKSPACE_NAME_MAX_LENGTH } from "./constants/workspaceName";
import { useCreateWorkspace } from "./models/useCreateWorkspace";

/**
 * 새 워크스페이스 이름 입력 카드.
 */
export default function CreateWorkspaceCard() {
  const {
    errorMessage,
    handleChange,
    handleSubmit,
    inputRef,
    isPending,
    isSubmittable,
    name,
  } = useCreateWorkspace();

  return (
    <Container>
      <Title>워크스페이스 이름을 입력하세요</Title>

      <Form onSubmit={handleSubmit}>
        <Field>
          <Counter>
            {name.length}/{WORKSPACE_NAME_MAX_LENGTH}
          </Counter>
          <TextField
            ref={inputRef}
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

        <Button
          type="submit"
          size="lg"
          isFullWidth
          disabled={!isSubmittable}
          isLoading={isPending}
        >
          워크스페이스 생성
        </Button>
      </Form>
    </Container>
  );
}

const Container = styled.section`
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 0.75rem; /* 12px */
  width: 100%;
  max-width: 28.75rem; /* 460px */
  padding: 3rem; /* 48px */
  border-radius: 1.5rem; /* 24px */
  background-color: ${({ theme }) => theme.neutral[0]};
  box-shadow: ${({ theme }) => theme.shadow02};
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
