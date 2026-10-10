import styled from "@emotion/styled";
import Skeleton from "@primitives/ui/Skeleton";

/** 녹음을 조회하는 동안 칸 자리를 채워 두는 뼈대. 녹음 한 칸과 같은 배치예요. */
export default function LoadingFallback() {
  return (
    <Container>
      <Skeleton width={3.25} height={3.25} radius={0.875} /> {/* 52px */}
      <Lines>
        <Skeleton width={5} />
        <Skeleton width={8} height={1} />
        <Skeleton width={14} />
      </Lines>
    </Container>
  );
}

const Container = styled.div`
  display: flex;
  flex: 1;
  align-items: center;
  gap: 1rem; /* 16px */
  width: 100%;
  min-height: 0;
  padding: 1.25rem; /* 20px */
  border-radius: 1rem; /* 16px */
  background-color: ${({ theme }) => theme.neutral[100]};
`;

const Lines = styled.div`
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 0.625rem; /* 10px */
  min-width: 0;
`;
