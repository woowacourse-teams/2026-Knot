import styled from "@emotion/styled";
import { ReactNode } from "react";

interface ChatPanelHeaderProps {
  children: ReactNode;
}

/**
 * 채팅 패널 상단의 Header 레이아웃
 *
 * `<header>`로 렌더링되며, 자식을 좌우 양 끝으로 분산 정렬합니다.
 * 좌측 영역(로고·제목·뒤로가기)과 우측 영역(액션 아이콘)을 각각 하나의 요소로 묶어 children으로 전달합니다.
 * 배치만 담당하므로 색상·간격 등 내부 스타일은 사용하는 쪽에서 정의합니다.
 *
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
