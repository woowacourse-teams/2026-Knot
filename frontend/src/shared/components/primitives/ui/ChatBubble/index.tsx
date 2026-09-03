import { ReactNode } from "react";
import styled from "@emotion/styled";

interface ChatBubbleProps {
  children: ReactNode;
}

/**
 * 사용자가 보낸 말풍선.
 *
 * 너비는 내용에 맞춰 늘어나되 630px에서 멈춥니다.
 *
 * @see https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=1209-807
 */
export default function ChatBubble({ children }: ChatBubbleProps) {
  return <Root>{children}</Root>;
}

const Root = styled.div`
  width: fit-content;
  max-width: min(39.375rem, 100%); /* 630px */
  padding: 0.75rem 1rem;
  white-space: pre-wrap; /* 입력창에서 넣은 줄바꿈을 그대로 보여줍니다 */
  overflow-wrap: anywhere; /* 긴 URL이 말풍선을 뚫고 나가지 않게 합니다 */
  border-radius: 0.875rem;
  background-color: ${({ theme }) => theme.neutral[100]};
  ${({ theme }) => theme.text.body01};
  color: ${({ theme }) => theme.neutral[900]};
`;
