import styled from "@emotion/styled";
import Skeleton from "@primitives/ui/Skeleton";

/**
 * 문서를 불러오는 동안 제목과 본문 자리를 채우는 덩어리.
 */
export default function DocumentSkeleton() {
  return (
    <Container>
      <TitleWrapper>
        {/* 덩어리 28px, 모서리 8px */}
        <Skeleton width="40%" height={1.75} radius={0.5} />
      </TitleWrapper>
      <Lines>
        {/* 짧은 줄(96px)은 구역 제목 자리, 긴 줄은 그 아래 문장 자리예요 */}
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

/** 실제 제목 한 줄(heading02 26px × 줄 높이 1.5)과 같은 높이를 지켜, 불러온 뒤 본문이 아래로 밀리지 않게 해요 */
const TitleWrapper = styled.div`
  display: flex;
  align-items: center;
  height: 2.4375rem; /* 39px */
`;

/** 덩어리 10px + 간격 14px이 본문 한 줄 높이(body01 16px × 줄 높이 1.5 = 24px)와 같아요 */
const Lines = styled.div`
  display: flex;
  flex-direction: column;
  gap: 0.875rem; /* 14px */
`;
