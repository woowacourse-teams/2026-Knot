import { ComponentProps } from "react";
import styled from "@emotion/styled";

interface TextareaProps extends ComponentProps<"textarea"> {}

/**
 * 여러 줄 텍스트 입력 UI.
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
