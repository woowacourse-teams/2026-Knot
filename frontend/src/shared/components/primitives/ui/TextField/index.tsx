import styled from "@emotion/styled";
import Input from "@primitives/ui/Input";
import type { InputHTMLAttributes } from "react";
import { useId } from "react";

interface TextFieldProps extends Omit<
  InputHTMLAttributes<HTMLInputElement>,
  "value"
> {
  value: string;
  errorMessage?: string;
}

/**
 * 라벨·글자 수·에러 메시지까지 함께 다루는 입력 필드.
 *
 * `maxLength`를 넘기면 입력창 오른쪽 위에 `현재 글자 수/최대 글자 수`가 표시되고,
 * `errorMessage`를 넘기면 입력창이 에러 스타일로 바뀌면서 아래에 메시지가 붙어요.
 *
 * @see https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=424-596 Field/TextField 컴포넌트 세트
 * @see https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=432-1325 status=입력 에러 (에러 메시지 포함)
 * @see https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=600-10133 글자 수 카운터와 함께 쓰인 화면
 */
export default function TextField({
  value,
  errorMessage,
  id,
  ...props
}: TextFieldProps) {
  const generatedId = useId();
  const inputId = id ?? generatedId;
  const errorMessageId = `${inputId}-error`;
  const isError = Boolean(errorMessage);

  return (
    <Container>
      <Input
        id={inputId}
        value={value}
        isError={isError}
        aria-describedby={isError ? errorMessageId : undefined}
        {...props}
      />
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

const ErrorMessage = styled.p`
  color: ${({ theme }) => theme.sub.warning[800]};
  ${({ theme }) => theme.text.caption02};
`;
