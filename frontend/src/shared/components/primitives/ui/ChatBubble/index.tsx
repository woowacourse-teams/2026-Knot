import { ReactNode } from "react";
import styled from "@emotion/styled";

interface ChatBubbleProps {
  children: ReactNode;
}

/**
 * 사용자가 보낸 말풍선.
 *
 * 동작 규칙은 스토리북 `Shared/ChatBubble`에서 확인해요.
 */
export default function ChatBubble({ children }: ChatBubbleProps) {
  return <Root>{children}</Root>;
}

const Root = styled.div`
  width: fit-content;
  max-width: min(39.375rem, 100%); /* 630px */
  padding: 0.75rem 1rem;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  border-radius: 0.875rem;
  background-color: ${({ theme }) => theme.neutral[100]};
  ${({ theme }) => theme.text.body01};
  color: ${({ theme }) => theme.neutral[900]};
`;
