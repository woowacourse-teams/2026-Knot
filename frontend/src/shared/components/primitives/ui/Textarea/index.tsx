import { ComponentProps } from "react";
import styled from "@emotion/styled";

interface TextareaProps extends ComponentProps<"textarea"> {}

/**
 * 여러 줄 텍스트 입력 UI.
 *
 * 쓰는 법은 스토리북 `Shared/Textarea`에서 확인해요.
 * @see https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=506-7322&t=gJ9xykBAewLJr7bt-11
 */

export default function Textarea({ ...props }: TextareaProps) {
  return <Root {...props} />;
}

const Root = styled.textarea`
  padding: 0;
  border: none;
  outline: none;
  resize: none;
`;
