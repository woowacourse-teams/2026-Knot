import styled from "@emotion/styled";
import type { ReactNode } from "react";

interface IllustratedMessageProps {
  /** 맨 위에 놓을 48×48 그림. `currentColor`로 그린 그림은 Primary 색을 따라요 */
  illustration: ReactNode;
  /** 한 줄 제목 */
  title: string;
  /** 제목 아래 설명. 한 줄씩 나눠 넘기면 그 자리에서 줄을 바꿔요 */
  description: string[];
  /** 설명 아래 놓을 버튼. 문구와 동작은 쓰는 쪽이 정해요 */
  action?: ReactNode;
}

/**
 * 그림 · 제목 · 설명 · 버튼을 가운데 세로로 놓는 안내.
 *
 * 문서 정리 화면의 세 상태(정리 중 · 정리 실패 · 내용 없음)는 간격과 글꼴이 같고 그림 · 문구 · 버튼만 달라요.
 * 그래서 간격과 글꼴은 이 컴포넌트가 갖고, 상태마다 다른 내용은 쓰는 쪽이 넘겨요.
 *
 * 간격과 글꼴은 피그마의 한 컴포넌트에서 함께 바뀌는 값이라 배치를 따로 떼어 두지 않았어요.
 * 같은 배치에 다른 글꼴을 쓰는 곳이 생기면 그때 배치를 떼어내요.
 *
 * @example
 * <IllustratedMessage
 *   illustration={<DocumentFailedIllustration size={48} />}
 *   title="문서를 만들지 못했어요"
 *   description={[
 *     "녹음은 보관해 두었어요.",
 *     "다시 시도하거나, 홈의 진행 중인 녹음에서 나중에 다시 시도할 수 있어요.",
 *   ]}
 *   action={<Button onClick={retry}>다시 시도</Button>}
 * />
 *
 * @see {@link https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=1814-16290 State/Drafting}
 * @see {@link https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=2262-33172 State/DocNotCreated}
 */
export default function IllustratedMessage({
  illustration,
  title,
  description,
  action,
}: IllustratedMessageProps) {
  return (
    <Container>
      <IllustrationWrapper>{illustration}</IllustrationWrapper>

      <TextContainer>
        <Title>{title}</Title>
        <Description>
          {description.map((line) => (
            <p key={line}>{line}</p>
          ))}
        </Description>
      </TextContainer>

      {action}
    </Container>
  );
}

const Container = styled.div`
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 1.25rem; /* 20px */
`;

// flex로 두어야 그림 아래에 글자 줄 높이만큼 빈틈이 생기지 않아요
const IllustrationWrapper = styled.div`
  display: flex;
  color: ${({ theme }) => theme.primary};
`;

const TextContainer = styled.div`
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 1rem; /* 16px */
  text-align: center;
  overflow-wrap: break-word;
`;

const Title = styled.h2`
  ${({ theme }) => theme.text.heading02};
  color: ${({ theme }) => theme.primary};
`;

const Description = styled.div`
  ${({ theme }) => theme.text.body02};
  color: ${({ theme }) => theme.neutral[700]};
`;
