import styled from "@emotion/styled";
import { ReactNode } from "react";

interface ChatPanelHeaderProps {
  children: ReactNode;
}

/**
 * 채팅 패널 상단의 Header 레이아웃
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
