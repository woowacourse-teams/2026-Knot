import styled from "@emotion/styled";
import IllustratedMessageLayout from "@primitives/layout/IllustratedMessageLayout";
import type { ReactNode } from "react";

interface DocumentStateMessageProps {
  /** 맨 위에 놓을 그림 */
  illustration: ReactNode;
  /** 무슨 일이 생겼는지 알리는 제목 */
  title: string;
  /** 제목 아래 설명 */
  description: string;
  /** 설명 아래 놓을 버튼 */
  button: ReactNode;
}

/**
 * 문서를 보여 주지 못할 때의 안내들이 함께 쓰는 글 모양.
 *
 * 그림 · 제목 · 설명 · 버튼의 배치는 `IllustratedMessageLayout`이 맡고, 여기서는 제목과 설명의 글꼴과 색만 정해요.
 * 낭독기가 바로 읽도록 `role="alert"`를 붙였어요.
 */
export default function DocumentStateMessage({
  illustration,
  title,
  description,
  button,
}: DocumentStateMessageProps) {
  return (
    <Root role="alert">
      <IllustratedMessageLayout
        illustration={illustration}
        title={<Title>{title}</Title>}
        description={<Description>{description}</Description>}
        button={button}
      />
    </Root>
  );
}

/** 피그마 State/DocNotCreated: 묶음 폭 518px */
const Root = styled.div`
  width: 100%;
  max-width: 32.375rem; /* 518px */
  margin: 0 auto;
`;

const Title = styled.h2`
  ${({ theme }) => theme.text.heading02};
  color: ${({ theme }) => theme.primary};
`;

const Description = styled.p`
  ${({ theme }) => theme.text.body02};
  color: ${({ theme }) => theme.neutral[700]};
`;
