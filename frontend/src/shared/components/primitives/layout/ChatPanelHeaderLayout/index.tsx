import styled from "@emotion/styled";
import { ReactNode } from "react";

interface ChatPanelHeaderProps {
  children: ReactNode;
}

/**
 * 채팅 패널 상단의 Header 레이아웃
 *
 * 자식을 넘기는 방법은 스토리북 `Shared/Layout/ChatPanelHeaderLayout`에서 확인해요.
 * @see https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=603-2867&t=NtCKbgE8RjHqh556-11
 */
export default function ChatPanelHeaderLayout({
  children,
}: ChatPanelHeaderProps) {
  return <Container>{children}</Container>;
}

const Container = styled.header`
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;
`;
