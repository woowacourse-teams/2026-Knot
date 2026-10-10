import styled from "@emotion/styled";
import Skeleton from "@primitives/ui/Skeleton";

/** 폴더마다 그릴 행의 수. 폴더 두 개에 행 두 개 · 한 개를 둬요 */
const FOLDER_ROW_COUNTS = [2, 1];

/**
 * 문서 목록을 불러오는 동안 폴더 이름 · 카드 · 행 자리를 채우는 덩어리.
 *
 * 폴더와 행의 간격 · 여백 · 높이를 실제 목록과 같게 지켜, 불러온 뒤 내용이 크게 움직이지 않게 해요.
 */
export default function DocumentListSkeleton() {
  return (
    <Container role="status" aria-label="문서 목록을 불러오고 있어요">
      {FOLDER_ROW_COUNTS.map((rowCount, folderIndex) => (
        <Folder key={folderIndex}>
          <TopicRow>
            <Skeleton width={5} />
          </TopicRow>
          <Card>
            {Array.from({ length: rowCount }, (_, rowIndex) => (
              <Row key={rowIndex}>
                <TextBlock>
                  <Line>
                    <Skeleton width="30%" />
                  </Line>
                  <Line>
                    <Skeleton width="60%" />
                  </Line>
                </TextBlock>
                <Skeleton width={8} />
              </Row>
            ))}
          </Card>
        </Folder>
      ))}
    </Container>
  );
}

/** 폴더 사이 28px */
const Container = styled.div`
  display: flex;
  flex-direction: column;
  gap: 1.75rem; /* 28px */
`;

/** 폴더 이름 줄과 카드 사이 12px */
const Folder = styled.div`
  display: flex;
  flex-direction: column;
  gap: 0.75rem; /* 12px */
`;

/** 폴더 이름 한 줄(label01 16px × 줄 높이 1.5)과 같은 높이, 왼쪽 여백 4px */
const TopicRow = styled.div`
  display: flex;
  align-items: center;
  height: 1.5rem; /* 24px */
  padding-left: 0.25rem; /* 4px */
`;

const Card = styled.div`
  overflow: hidden;
  border-radius: 1.25rem; /* 20px */
  background-color: ${({ theme }) => theme.neutral[0]};
  box-shadow: ${({ theme }) => theme.shadow02};
`;

/** 실제 행과 같은 여백(20px 24px)과 행 사이 선 */
const Row = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem; /* 16px */
  padding: 1.25rem 1.5rem; /* 20px 24px */

  & + & {
    border-top: 1px solid ${({ theme }) => theme.neutral[200]};
  }
`;

/** 제목 줄과 요약 줄 사이 4px */
const TextBlock = styled.div`
  display: flex;
  flex: 1 0 0;
  flex-direction: column;
  gap: 0.25rem; /* 4px */
`;

/** 글 한 줄(16px × 줄 높이 1.5)과 같은 높이 */
const Line = styled.div`
  display: flex;
  align-items: center;
  height: 1.5rem; /* 24px */
`;
