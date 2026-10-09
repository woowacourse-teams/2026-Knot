import styled from "@emotion/styled";
import IllustratedMessageLayout from "@primitives/layout/IllustratedMessageLayout";
import type { ReactNode } from "react";

interface StateMessageProps {
  /** 제목 요소에 붙일 id. 정리 화면 섹션이 `aria-labelledby`로 제목을 가리켜요 */
  titleId: string;
  /** 맨 위에 놓을 그림 */
  illustration: ReactNode;
  /** 지금 상태를 알리는 제목 */
  title: string;
  /** 제목 아래 설명. 항목 하나가 한 줄이에요. 비워 두면 설명을 그리지 않아요 */
  descriptionLines?: string[];
  /** 설명 아래 놓을 버튼. 없으면 그리지 않아요 */
  button?: ReactNode;
}

/**
 * 정리 화면의 상태 화면들이 함께 쓰는 글 모양.
 *
 * 그림 · 제목 · 설명 · 버튼의 배치는 `IllustratedMessageLayout`이 맡고, 여기서는 제목과 설명의 글꼴과 색만 정해요.
 */
export default function StateMessage({
  titleId,
  illustration,
  title,
  descriptionLines = [],
  button,
}: StateMessageProps) {
  return (
    <IllustratedMessageLayout
      illustration={illustration}
      title={<Title id={titleId}>{title}</Title>}
      description={
        descriptionLines.length > 0 && (
          <Description>
            {descriptionLines.map((line) => (
              <p key={line}>{line}</p>
            ))}
          </Description>
        )
      }
      button={button}
    />
  );
}

const Title = styled.h2`
  ${({ theme }) => theme.text.heading02};
  color: ${({ theme }) => theme.primary};
`;

const Description = styled.div`
  ${({ theme }) => theme.text.body02};
  color: ${({ theme }) => theme.neutral[700]};
`;
