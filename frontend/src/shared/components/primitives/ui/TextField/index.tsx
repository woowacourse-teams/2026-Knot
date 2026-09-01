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
  /** 검증을 통과했을 때 아래에 보여줄 메시지. `errorMessage`가 있으면 에러가 우선해요 */
  successMessage?: string;
  /** 우측에 표시할 컴포넌트 */
  rightComponent?: React.ReactNode;
}

interface GetStatusParams {
  isError: boolean;
  isSuccess: boolean;
  value: string;
}

const getStatus = ({ isError, isSuccess, value }: GetStatusParams) => {
  if (isError) return "error";
  if (isSuccess) return "success";
  return value.length > 0 ? "filled" : "empty";
};

/**
 * 에러 메시지까지 함께 다루는 입력 필드.
 *
 * 값과 에러 메시지로 입력창의 `status`를 계산해 넘기므로,
 * `Input`은 상태 판단 없이 받은 status만 그려요.
 *
 * `errorMessage`를 넘기면 입력창이 에러 스타일로 바뀌면서 아래에 메시지가 생기고,
 * `successMessage`를 넘기면 성공 스타일로 바뀌면서 아래에 메시지가 생겨요.
 * 둘 다 있으면 에러가 우선하고, 보이는 메시지는 `aria-describedby`로 입력창과 연결돼요.
 *
 * `id`를 직접 넘기지 않으면 `useId`로 만든 값을 쓰므로,
 * 외부 `label`과 연결해야 할 때만 `id`를 넘기면 돼요.
 *
 * @see https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=424-596 Field/TextField 컴포넌트 세트
 * @see https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=432-1325 status=입력 에러 (에러 메시지 포함)
 * @see https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=627-2967 Field/TextField/Code status=로딩 (`isLoading`)
 * @see https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=664-552 Field/TextField/Code status=인증 완료 (`successMessage`)
 */
export default function TextField({
  value,
  variant,
  errorMessage,
  successMessage,
  rightComponent,
  readOnly = false,
  id,
  ...props
}: TextFieldProps) {
  const generatedId = useId();
  const inputId = id ?? generatedId;
  const messageId = `${inputId}-message`;
  const isError = Boolean(errorMessage);
  const isSuccess = !isError && Boolean(successMessage);
  const hasMessage = isError || isSuccess;

  return (
    <Container>
      <InputWrapper>
        <Input
          id={inputId}
          value={value}
          variant={variant}
          status={getStatus({ isError, isSuccess, value })}
          readOnly={readOnly}
          aria-describedby={hasMessage ? messageId : undefined}
          {...props}
        />
        {rightComponent && (
          <RightComponentWrapper>{rightComponent}</RightComponentWrapper>
        )}
      </InputWrapper>
      {isError && <ErrorMessage id={messageId}>{errorMessage}</ErrorMessage>}
      {isSuccess && (
        <SuccessMessage id={messageId}>{successMessage}</SuccessMessage>
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

const Message = styled.p`
  ${({ theme }) => theme.text.caption02};
`;

const ErrorMessage = styled(Message)`
  color: ${({ theme }) => theme.sub.warning[800]};
`;

const SuccessMessage = styled(Message)`
  color: ${({ theme }) => theme.sub.accent[500]};
`;
