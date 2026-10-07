import useDocumentQuery from "@api/queries/useDocumentQuery";
import styled from "@emotion/styled";
import Breadcrumb from "@primitives/ui/Breadcrumb";
import Chip from "@primitives/ui/Chip";
import { formatDate } from "@utils/formatDate";
import { formatDurationFromSeconds } from "@utils/formatDurationFromSeconds";
import { useParams } from "react-router";

interface DocumentHeaderProps {
  /** 보여줄 문서의 ID */
  documentId: number;
  /** 제목 요소에 붙일 id. 문서 보기 섹션이 `aria-labelledby`로 제목을 가리킬 때 넘겨요 */
  titleId?: string;
}

/**
 * 문서 머리. 경로 · 제목 · 만든 날짜 · 녹음 길이를 보여줘요.
 *
 * 문서 상세는 문서 보기 위젯과 같은 키로 조회해서 요청이 더 나가지 않고 캐시를 나눠 써요.
 * 문서를 아직 받지 못했으면 아무것도 그리지 않아요. 불러오는 중 · 실패 화면은 위젯이 맡아요.
 * 확인 수와 복사 버튼은 다음 작업에서 더해요.
 */
export default function DocumentHeader({
  documentId,
  titleId,
}: DocumentHeaderProps) {
  const { workspaceId } = useParams();
  const { data: documentDetail } = useDocumentQuery({
    workspaceId: Number(workspaceId),
    documentId,
  });

  if (documentDetail === undefined) return null;

  return (
    <Container>
      <Breadcrumb parent="문서" current={documentDetail.title} />
      <TitleBlock>
        <Title id={titleId}>{documentDetail.title}</Title>
        <Meta>
          <CreatedDate dateTime={documentDetail.createdAt}>
            {formatDate(documentDetail.createdAt)}
          </CreatedDate>
          <Chip>
            {formatDurationFromSeconds(documentDetail.recordingDurationSeconds)}
          </Chip>
        </Meta>
      </TitleBlock>
    </Container>
  );
}

/** 피그마 문서 열(Standard): 경로 줄과 제목 묶음 사이 24px */
const Container = styled.div`
  display: flex;
  flex-direction: column;
  gap: 1.5rem; /* 24px */
`;

/** 피그마 TitleBlock: 제목과 날짜 줄 사이 4px */
const TitleBlock = styled.div`
  display: flex;
  flex-direction: column;
  gap: 0.25rem; /* 4px */
`;

const Title = styled.h2`
  ${({ theme }) => theme.text.heading02};
  color: ${({ theme }) => theme.neutral[900]};
  overflow-wrap: break-word;
`;

/** 피그마 Meta: 날짜와 녹음 길이 칩 사이 8px */
const Meta = styled.div`
  display: flex;
  align-items: center;
  gap: 0.5rem; /* 8px */
`;

const CreatedDate = styled.time`
  ${({ theme }) => theme.text.caption02};
  color: ${({ theme }) => theme.neutral[500]};
`;
