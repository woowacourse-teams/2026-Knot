import styled from "@emotion/styled";
import Skeleton from "@primitives/ui/Skeleton";

/**
 * 문서를 불러오는 동안 제목과 본문 자리를 채우는 덩어리.
 */
export default function DocumentSkeleton() {
  return (
    <Container>
      <Skeleton width="40%" height={1.75} radius={0.5} />
      <Lines>
        <Skeleton width={6} />
        <Skeleton />
        <Skeleton width="85%" />
        <Skeleton width={6} />
        <Skeleton width="70%" />
      </Lines>
    </Container>
  );
}

/** 문서 열의 제목 → 본문 간격(피그마 Standard gap 24px)을 그대로 써요 */
const Container = styled.div`
  display: flex;
  flex-direction: column;
  gap: 1.5rem; /* 24px */
`;

const Lines = styled.div`
  display: flex;
  flex-direction: column;
  gap: 0.875rem; /* 14px */
`;
