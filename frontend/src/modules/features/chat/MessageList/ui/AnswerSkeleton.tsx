import styled from "@emotion/styled";
import Skeleton from "@primitives/ui/Skeleton";

/**
 * 답변이 아직 한 글자도 오지 않았을 때 그 자리에 두는 뼈대.
 *
 * 첫 조각이 도착할 때까지 답변 자리가 비어 있으면 화면이 멈춘 것처럼 보이고, 글이 도착하는 순간
 * 아래 내용이 밀려 튑니다. 들어올 문단의 모양을 미리 잡아 두면 둘 다 덜합니다.
 *
 * 줄 길이는 답변 한 문단의 생김새(짧은 머리말 + 본문 세 줄)를 따랐어요.
 *
 * @see {@link https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=1283-7940 탐색 결과/답변 대기}
 */
export default function AnswerSkeleton() {
  return (
    <Container>
      <Skeleton width="19%" />
      <Skeleton />
      <Skeleton width="77%" />
      <Skeleton width="51%" />
    </Container>
  );
}

const Container = styled.div`
  display: flex;
  flex-direction: column;
  gap: 0.75rem; /* 12px */
  width: 100%;
  padding: 0.375rem 0; /* 6px — 글줄 자리와 높이를 맞춰요 */
`;
