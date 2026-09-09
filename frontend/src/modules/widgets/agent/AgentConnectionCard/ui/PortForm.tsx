import styled from "@emotion/styled";
import Button from "@primitives/ui/Button";
import TextField from "@primitives/ui/TextField";
import type { FormEvent } from "react";

interface PortFormProps {
  value: string;
  errorMessage?: string;
  isApplying: boolean;
  onChange: (value: string) => void;
  onSubmit: () => void;
}

const PORT_INPUT_ID = "agent-connection-port";

/**
 * MCP 서버 포트 변경 폼.
 *
 * 기본 포트(47871)가 다른 프로그램과 겹치면 여기서 바꿔요. 바꾸면 셸이 서버를 다시 열고,
 * 등록 스니펫의 주소도 바뀌므로 CLI에 다시 등록해야 해요(로드맵 Q47).
 */
export default function PortForm({
  value,
  errorMessage,
  isApplying,
  onChange,
  onSubmit,
}: PortFormProps) {
  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    onSubmit();
  };

  return (
    <Form onSubmit={handleSubmit} aria-label="포트 변경">
      <Label htmlFor={PORT_INPUT_ID}>포트</Label>
      <Fields>
        <FieldWrapper>
          <TextField
            id={PORT_INPUT_ID}
            value={value}
            inputMode="numeric"
            autoComplete="off"
            placeholder="47871"
            errorMessage={errorMessage}
            onChange={(event) => onChange(event.target.value)}
          />
        </FieldWrapper>
        <Button
          type="submit"
          size="md"
          variant="outline"
          isLoading={isApplying}
        >
          포트 변경
        </Button>
      </Fields>
      <Hint>바꾸면 등록 명령의 주소가 달라져 CLI에 다시 등록해야 해요.</Hint>
    </Form>
  );
}

const Form = styled.form`
  display: flex;
  flex-direction: column;
  gap: 0.5rem; /* 8px */
  width: 100%;
`;

const Label = styled.label`
  ${({ theme }) => theme.text.label01};
  color: ${({ theme }) => theme.neutral[900]};
`;

const Fields = styled.div`
  display: flex;
  align-items: flex-start;
  gap: 0.75rem; /* 12px */
`;

const FieldWrapper = styled.div`
  flex: 1;
  min-width: 0;
`;

const Hint = styled.p`
  ${({ theme }) => theme.text.caption02};
  color: ${({ theme }) => theme.neutral[500]};
`;
