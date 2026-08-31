import styled from "@emotion/styled";
import Input, { type InputVariant } from "@primitives/ui/Input";
import type { InputHTMLAttributes } from "react";
import { useId } from "react";

interface TextFieldProps extends Omit<
  InputHTMLAttributes<HTMLInputElement>,
  "value"
> {
  value: string;
  variant?: InputVariant;
  errorMessage?: string;
  /** 우측에 표시할 컴포넌트 */
  rightComponent?: React.ReactNode;
}

/**
 * 에러 메시지까지 함께 다루는 입력 필드.
 *
 * 값과 에러 메시지로 입력창의 `status`를 계산해 넘기므로,
 * `Input`은 상태 판단 없이 받은 status만 그려요.
 *
 * `errorMessage`를 넘기면 입력창이 에러 스타일로 바뀌면서 아래에 메시지가 생기고,
 * 그 메시지는 `aria-describedby`로 입력창과 연결돼요.
 *
 * `id`를 직접 넘기지 않으면 `useId`로 만든 값을 쓰므로,
 * 외부 `label`과 연결해야 할 때만 `id`를 넘기면 돼요.
 *
 * @see https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=424-596 Field/TextField 컴포넌트 세트
 * @see https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=432-1325 status=입력 에러 (에러 메시지 포함)
 * @see https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=627-2967 Field/TextField/Code status=로딩 (`isLoading`)
 */
export default function TextField({
  value,
  variant,
  errorMessage,
  rightComponent,
  readOnly = false,
  id,
  ...props
}: TextFieldProps) {
  const generatedId = useId();
  const inputId = id ?? generatedId;
  const errorMessageId = `${inputId}-error`;
  const isError = Boolean(errorMessage);

  return (
    <Container>
      <InputWrapper>
        <Input
          id={inputId}
          value={value}
          variant={variant}
          status={isError ? "error" : value.length > 0 ? "filled" : "empty"}
          readOnly={readOnly}
          aria-describedby={isError ? errorMessageId : undefined}
          {...props}
        />
        {rightComponent && (
          <RightComponentWrapper>{rightComponent}</RightComponentWrapper>
        )}
      </InputWrapper>
      {isError && (
        <ErrorMessage id={errorMessageId}>{errorMessage}</ErrorMessage>
      )}
    </Container>
  );
}

const Container = styled.div`
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  width: 100%;
`;

const InputWrapper = styled.div`
  position: relative;
  display: flex;
  flex-direction: column;
  width: 100%;
`;

const RightComponentWrapper = styled.span`
  position: absolute;
  top: 0;
  right: 0.9375rem; /* 15px */
  bottom: 0;
  display: flex;
  align-items: center;
  color: ${({ theme }) => theme.neutral[500]};
`;

const ErrorMessage = styled.p`
  color: ${({ theme }) => theme.sub.warning[800]};
  ${({ theme }) => theme.text.caption02};
`;
