import styled from "@emotion/styled";
import Button from "@primitives/ui/Button";
import type { FormEvent } from "react";

import type { LlmSettingsView } from "@/shared/types/desktop";

interface ModelSettingsFormProps {
  /** 아직 읽지 못했으면 null. 목록은 셸이 줘요 */
  settings: LlmSettingsView | null;
  modelValue: string;
  effortValue: string;
  isDirty: boolean;
  isSaving: boolean;
  isSaved: boolean;
  errorMessage?: string;
  onModelChange: (model: string) => void;
  onEffortChange: (effort: string) => void;
  onSubmit: () => void;
}

const MODEL_SELECT_ID = "claude-subscription-model";
const EFFORT_SELECT_ID = "claude-subscription-effort";

/**
 * 구독 호출의 모델·effort 설정 폼.
 *
 * 고를 수 있는 값은 셸이 주는 목록(`settings.models`·`settings.efforts`)뿐이에요(로드맵 Q62).
 * 바꾼 값은 저장을 눌러야 셸에 쓰이고, 다음 질문부터 적용돼요.
 */
export default function ModelSettingsForm({
  settings,
  modelValue,
  effortValue,
  isDirty,
  isSaving,
  isSaved,
  errorMessage,
  onModelChange,
  onEffortChange,
  onSubmit,
}: ModelSettingsFormProps) {
  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    onSubmit();
  };

  return (
    <Form onSubmit={handleSubmit} aria-label="모델 설정">
      <Field>
        <Label htmlFor={MODEL_SELECT_ID}>모델</Label>
        <Select
          id={MODEL_SELECT_ID}
          value={modelValue}
          disabled={settings === null}
          onChange={(event) => onModelChange(event.target.value)}
        >
          {settings?.models.map((model) => (
            <option key={model} value={model}>
              {model}
            </option>
          ))}
        </Select>
      </Field>

      <Field>
        <Label htmlFor={EFFORT_SELECT_ID}>effort</Label>
        <Select
          id={EFFORT_SELECT_ID}
          value={effortValue}
          disabled={settings === null}
          onChange={(event) => onEffortChange(event.target.value)}
        >
          {settings?.efforts.map((effort) => (
            <option key={effort} value={effort}>
              {effort}
            </option>
          ))}
        </Select>
        <Hint>
          effort가 높을수록 더 오래 생각하고 더 많은 토큰을 써요. 다음 질문부터
          적용돼요.
        </Hint>
      </Field>

      <Actions>
        {isSaved && <SavedText role="status">저장됨</SavedText>}
        <Button
          type="submit"
          size="md"
          variant="outline"
          disabled={!isDirty}
          isLoading={isSaving}
        >
          저장
        </Button>
      </Actions>

      {errorMessage && <ErrorMessage role="alert">{errorMessage}</ErrorMessage>}
    </Form>
  );
}

const Form = styled.form`
  display: flex;
  flex-direction: column;
  gap: 1rem; /* 16px */
  width: 100%;
`;

const Field = styled.div`
  display: flex;
  flex-direction: column;
  gap: 0.5rem; /* 8px */
`;

const Label = styled.label`
  ${({ theme }) => theme.text.label01};
  color: ${({ theme }) => theme.neutral[900]};
`;

const Select = styled.select`
  width: 100%;
  padding: 0.75rem 1rem; /* 12px 16px */
  border: 1px solid ${({ theme }) => theme.neutral[200]};
  border-radius: 0.75rem; /* 12px */
  background-color: ${({ theme }) => theme.neutral[0]};
  ${({ theme }) => theme.text.body01};
  color: ${({ theme }) => theme.neutral[900]};

  &:disabled {
    color: ${({ theme }) => theme.neutral[400]};
  }

  &:focus-visible {
    outline: 2px solid ${({ theme }) => theme.neutral[400]};
    outline-offset: 2px;
  }
`;

const Hint = styled.p`
  ${({ theme }) => theme.text.caption02};
  color: ${({ theme }) => theme.neutral[500]};
`;

const Actions = styled.div`
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 0.75rem; /* 12px */
`;

const SavedText = styled.span`
  ${({ theme }) => theme.text.caption02};
  color: ${({ theme }) => theme.neutral[600]};
`;

const ErrorMessage = styled.p`
  ${({ theme }) => theme.text.caption02};
  color: ${({ theme }) => theme.sub.warning[800]};
`;
