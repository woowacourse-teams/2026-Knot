import { ComponentProps } from "react";
import styled from "@emotion/styled";

interface TextareaProps extends ComponentProps<"textarea"> {}

/**
 * 여러 줄 텍스트 입력 UI.
 * 브라우저 기본 스타일만 제거한 primitive 컴포넌트입니다.
 * 배경, 보더, 폰트, 높이 제한은 사용처에서 지정합니다.
 *
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
